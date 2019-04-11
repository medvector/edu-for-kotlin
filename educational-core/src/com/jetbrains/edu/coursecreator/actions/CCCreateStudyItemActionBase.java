package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.actions.sections.CCWrapWithSection;
import com.jetbrains.edu.coursecreator.ui.AdditionalPanel;
import com.jetbrains.edu.coursecreator.ui.CCItemPositionPanel;
import com.jetbrains.edu.coursecreator.yaml.YamlFormatSynchronizer;
import com.jetbrains.edu.learning.EduUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.configuration.EduConfigurator;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.courseFormat.ext.CourseExt;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.statistics.FeedbackSenderKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class CCCreateStudyItemActionBase<Item extends StudyItem> extends DumbAwareAction {
  protected static final Logger LOG = Logger.getInstance(CCCreateStudyItemActionBase.class);

  private final StudyItemType myItemType;

  public CCCreateStudyItemActionBase(@NotNull StudyItemType itemType, Icon icon) {
    super(StringUtil.toTitleCase(itemType.getPresentableName()), "Create New " + StringUtil.toTitleCase(itemType.getPresentableName()),
          icon);
    myItemType = itemType;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    final VirtualFile[] selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (!isActionApplicable(project, selectedFiles)) return;

    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) return;

    VirtualFile itemFile = createItem(project, selectedFiles[0], course);
    if (itemFile != null) {
      ProjectView.getInstance(project).select(itemFile, itemFile, true);
    }
    askFeedback(course, project);
    if (StudyItemType.LESSON.equals(myItemType)) {
      suggestWrapLessonsIntoSection(project, course, selectedFiles[0]);
    }
  }

  private void suggestWrapLessonsIntoSection(@NotNull final Project project,
                                             @NotNull final Course course,
                                             @NotNull final VirtualFile sourceDirectory) {
    StudyItem parentItem = getParentItem(course, sourceDirectory);
    if (parentItem != course) {
      return;
    }
    final List<Lesson> lessonsToWrap = course.getLessons();
    if (lessonsToWrap.size() != 20) {
      return;
    }
    Notification notification = new Notification("WrapLessons", "Wrap Lessons With Section",
                                                 "Lessons can be wrapped with section", NotificationType.INFORMATION);
    notification.addAction(new DumbAwareAction("Wrap lessons") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        CCWrapWithSection.wrapLessonsIntoSection(project, course, lessonsToWrap);
        notification.expire();
      }
    });
    notification.notify(project);
  }

  private static void askFeedback(@NotNull final Course course, @NotNull final Project project) {
    if (FeedbackSenderKt.isFeedbackAsked()) {
      return;
    }
    final Ref<Integer> countTasks = new Ref<>(0);
    course.visitLessons((lesson) -> {
      countTasks.set(countTasks.get() + lesson.getTaskList().size());
      return;
    });
    if (countTasks.get() == 5) {
      FeedbackSenderKt.showNotification(false, course, project);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setEnabledAndVisible(false);
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final VirtualFile[] selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (!isActionApplicable(project, selectedFiles)) return;

    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) return;

    VirtualFile sourceDirectory = selectedFiles[0];
    if (!isAddedAsLast(sourceDirectory, project, course) && getThresholdItem(course, sourceDirectory) == null) return;
    if (CommonDataKeys.PSI_FILE.getData(event.getDataContext()) != null) return;
    presentation.setEnabledAndVisible(true);
  }

  private static boolean isActionApplicable(@Nullable Project project, @Nullable VirtualFile[] selectedFiles) {
    if (project == null || selectedFiles == null) return false;
    if (selectedFiles.length == 0 || selectedFiles.length > 1) return false;

    if (!EduUtils.isStudyProject(project) || !CCUtils.isCourseCreator(project)) return false;

    final VirtualFile selectedFile = selectedFiles[0];
    return selectedFile != null;
  }

  @Nullable
  protected VirtualFile getParentDir(@NotNull Project project, @NotNull Course course, @NotNull VirtualFile directory) {
    if (isAddedAsLast(directory, project, course)) {
      return directory;
    }
    return directory.getParent();
  }

  @Nullable
  public VirtualFile createItem(@NotNull final Project project, @NotNull final VirtualFile sourceDirectory,
                                @NotNull final Course course) {
    StudyItem parentItem = getParentItem(course, sourceDirectory);
    final Item item =
      getItem(sourceDirectory, project, course, parentItem);
    if (item == null) {
      LOG.info("Failed to create study item");
      return null;
    }
    final VirtualFile parentDir = getParentDir(project, course, sourceDirectory);
    if (parentDir == null) {
      LOG.info("Failed to get parent directory");
      return null;
    }
    CCUtils.updateHigherElements(parentDir.getChildren(), getStudyOrderable(item, course), item.getIndex() - 1, 1);
    addItem(course, item);
    sortSiblings(course, parentItem);
    VirtualFile virtualFile = createItemDir(project, item, parentDir, course);
    YamlFormatSynchronizer.saveItem(item);
    YamlFormatSynchronizer.saveItem(item.getParent());
    EduUsagesCollector.newStudyItemCreated(item);
    return virtualFile;
  }

  protected abstract void addItem(@NotNull final Course course, @NotNull final Item item);

  protected abstract Function<VirtualFile, ? extends StudyItem> getStudyOrderable(@NotNull final StudyItem item,
                                                                                  @NotNull Course course);

  protected abstract VirtualFile createItemDir(@NotNull final Project project, @NotNull final Item item,
                                               @NotNull final VirtualFile parentDirectory, @NotNull final Course course);

  @Nullable
  protected Item getItem(@NotNull final VirtualFile sourceDirectory,
                         @NotNull final Project project,
                         @NotNull final Course course,
                         @Nullable StudyItem parentItem) {
    int index;
    String suggestedName;
    List<AdditionalPanel> additionalPanels = new ArrayList<>();
    if (isAddedAsLast(sourceDirectory, project, course)) {
      index = getSiblingsSize(course, parentItem) + 1;
      suggestedName = getItemType().getPresentableName() + index;
    }
    else {
      StudyItem thresholdItem = getThresholdItem(course, sourceDirectory);
      if (thresholdItem == null) {
        return null;
      }

      index = thresholdItem.getIndex();
      String itemName = getItemType().getPresentableName();
      suggestedName = itemName + (index + 1);
      additionalPanels.add(new CCItemPositionPanel(thresholdItem.getName()));
    }
    if (parentItem == null) {
      return null;
    }
    VirtualFile parentItemDir = parentItem.getDir(project);
    if (parentItemDir == null) {
      return null;
    }
    NewStudyItemUiModel model = new NewStudyItemUiModel(parentItem, parentItemDir, getItemType(), suggestedName, index);
    NewStudyItemInfo info = showCreateStudyItemDialog(project, course, model, additionalPanels);
    if (info == null) {
      return null;
    }
    return createAndInitItem(project, course, parentItem, info);
  }

  @Nullable
  protected NewStudyItemInfo showCreateStudyItemDialog(@NotNull Project project,
                                                       @NotNull Course course,
                                                       @NotNull NewStudyItemUiModel model,
                                                       @NotNull List<AdditionalPanel> additionalPanels) {
    EduConfigurator<?> configurator = CourseExt.getConfigurator(course);
    if (configurator == null) {
      return null;
    }
    return configurator.getCourseBuilder().showNewStudyItemUi(project, model, additionalPanels);
  }

  protected abstract int getSiblingsSize(@NotNull final Course course, @Nullable final StudyItem parentItem);

  @NotNull
  protected StudyItemType getItemType() {
    return myItemType;
  }

  @Nullable
  protected abstract StudyItem getParentItem(@NotNull final Course course, @NotNull final VirtualFile directory);

  @Nullable
  protected abstract StudyItem getThresholdItem(@NotNull final Course course, @NotNull final VirtualFile sourceDirectory);

  protected abstract boolean isAddedAsLast(@NotNull final VirtualFile sourceDirectory,
                                           @NotNull final Project project,
                                           @NotNull final Course course);

  protected abstract void sortSiblings(@NotNull final Course course, @Nullable final StudyItem parentItem);


  public abstract Item createAndInitItem(@NotNull Project project,
                                         @NotNull final Course course,
                                         @Nullable final StudyItem parentItem,
                                         @NotNull NewStudyItemInfo info);
}
