package com.jetbrains.edu.python.learning.newproject;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.EduCourseBuilder;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.OpenApiExtKt;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils;
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkExtKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static com.jetbrains.edu.python.learning.PyEduUtils.installRequiredPackages;

public class PyCourseProjectGenerator extends CourseProjectGenerator<PyNewProjectSettings> {
  private static final Logger LOG = Logger.getInstance(PyCourseProjectGenerator.class);

  public PyCourseProjectGenerator(@NotNull EduCourseBuilder<PyNewProjectSettings> builder, @NotNull Course course) {
    super(builder, course);
  }

  @Override
  public void createAdditionalFiles(@NotNull Project project, @NotNull VirtualFile baseDir) throws IOException {
    final String testHelper = EduNames.TEST_HELPER;
    if (baseDir.findChild(testHelper) != null) return;
    final String templateText = GeneratorUtils.getInternalTemplateText("test_helper");
    GeneratorUtils.createChildFile(baseDir, testHelper, templateText);
  }

  @Override
  public void afterProjectGenerated(@NotNull Project project, @NotNull PyNewProjectSettings settings) {
    super.afterProjectGenerated(project, settings);
    Sdk sdk = settings.getSdk();

    if (sdk != null && sdk.getSdkType() == PyFakeSdkType.INSTANCE) {
      createAndAddVirtualEnv(project, settings);
      sdk = settings.getSdk();
    }
    sdk = updateSdkIfNeeded(project, sdk);
    SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk);

    if (sdk == null) {
      return;
    }
    installRequiredPackages(project, sdk);
  }

  public void createAndAddVirtualEnv(@NotNull Project project, @NotNull PyNewProjectSettings settings) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final String baseSdkPath = getBaseSdkPath(settings, course);
    if (baseSdkPath != null) {
      final PyDetectedSdk baseSdk = new PyDetectedSdk(baseSdkPath);
      final String virtualEnvPath = project.getBasePath() + "/.idea/VirtualEnvironment";
      final Sdk sdk = PySdkExtKt.createSdkByGenerateTask(new Task.WithResult<String, ExecutionException>(project,
              "Creating Virtual Environment",
              false) {
        @Override
        protected String compute(@NotNull ProgressIndicator indicator) throws ExecutionException {
          indicator.setIndeterminate(true);
          final PyPackageManager packageManager = PyPackageManager.getInstance(baseSdk);
          return packageManager.createVirtualEnv(virtualEnvPath, false);
        }
      }, getAllSdks(), baseSdk, project.getBasePath(), null);
      if (sdk == null) {
        LOG.warn("Failed to create virtual env in " + virtualEnvPath);
        return;
      }
      settings.setSdk(sdk);
      SdkConfigurationUtil.addSdk(sdk);
      PySdkExtKt.associateWithModule(sdk, null, project.getBasePath());
    }
  }

  @Nullable
  private static String getBaseSdkPath(@NotNull PyNewProjectSettings settings, Course course) {
    if (OpenApiExtKt.isUnitTestMode()) {
      Sdk sdk = settings.getSdk();
      return sdk != null ? sdk.getHomePath() : null;
    }
    return PyLanguageSettings.getBaseSdk(course);
  }

  @Nullable
  private static Sdk updateSdkIfNeeded(@NotNull Project project, @Nullable Sdk sdk) {
    PySdkSettingsHelper helper = PySdkSettingsHelper.firstAvailable();
    return helper.updateSdkIfNeeded(project, sdk);
  }

  @NotNull
  private static List<Sdk> getAllSdks() {
    PySdkSettingsHelper helper = PySdkSettingsHelper.firstAvailable();
    return helper.getAllSdks();
  }
}
