package com.jetbrains.edu.learning.stepik.submissions

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.courseFormat.ext.getVirtualFile
import com.jetbrains.edu.learning.courseFormat.ext.isTestFile
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseFormat.tasks.choice.ChoiceTask
import com.jetbrains.edu.learning.document
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.StepikNames
import com.jetbrains.edu.learning.stepik.StepikSolutionsLoader
import com.jetbrains.edu.learning.stepik.api.Reply
import com.jetbrains.edu.learning.stepik.api.SolutionFile
import com.jetbrains.edu.learning.stepik.api.Submission
import com.jetbrains.edu.learning.taskDescription.ui.*
import com.jetbrains.edu.learning.taskDescription.ui.styleManagers.StyleManager
import com.jetbrains.edu.learning.taskDescription.ui.styleManagers.StyleResourcesManager
import com.jetbrains.edu.learning.taskDescription.ui.styleManagers.TaskDescriptionBundle
import com.jetbrains.edu.learning.taskDescription.ui.tab.AdditionalTab
import com.jetbrains.edu.learning.taskDescription.ui.tab.SwingTabPanel
import com.jetbrains.edu.learning.taskDescription.ui.tab.TabManager.TabType.SUBMISSIONS_TAB
import com.jetbrains.edu.learning.taskDescription.ui.tab.TabPanel
import com.jetbrains.edu.learning.ui.EduColors
import icons.EducationalCoreIcons
import java.net.URL
import java.text.DateFormat
import java.util.*
import java.util.stream.Collectors
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.math.roundToInt

class SubmissionsTab(project: Project, val task: Task) : AdditionalTab(project, SUBMISSIONS_TAB) {

  init {
    val submissionsManager = SubmissionsManager.getInstance(project)
    val descriptionText = StringBuilder()
    val submissionsList = submissionsManager.getSubmissionsFromMemory(setOf(task.id))

    if (submissionsManager.isLoggedIn()) {
      if (submissionsList != null) {
        when {
          task is ChoiceTask -> addViewOnStepikLink(descriptionText, task)
          submissionsList.isEmpty() -> descriptionText.append(
            "<a ${StyleManager().textStyleHeader}>${EduCoreBundle.message("submissions.empty")}")
          else -> {
            addSubmissionsToText(submissionsList, descriptionText)
            addHyperlinkListener(getSubmissionsListener(project, submissionsManager, task))
          }
        }
      }
      else {
        descriptionText.append("<a ${StyleManager().textStyleHeader}>${EduCoreBundle.message("submissions.empty")}")
      }
    }
    else {
      addLoginLink(descriptionText, submissionsManager)
    }
    setText(descriptionText.toString(), plain = true)
  }

  override fun createTabPanel(): TabPanel {
    return SwingTabPanel(project, SUBMISSIONS_TAB)
  }

  private fun addViewOnStepikLink(descriptionText: StringBuilder, currentTask: ChoiceTask) {
    descriptionText.append(
      "<a ${StyleManager().textStyleHeader};color:#${ColorUtil.toHex(EduColors.hyperlinkColor)} " +
      "href=https://stepik.org/submissions/${currentTask.id}?unit=${currentTask.lesson.unitId}\">" +
      EduCoreBundle.message("submissions.view.quiz.on.stepik", StepikNames.STEPIK, "</a><a ${StyleManager().textStyleHeader}>"))
    addHyperlinkListener(EduBrowserHyperlinkListener.INSTANCE)
  }

  private fun addLoginLink(descriptionText: StringBuilder,
                           submissionsManager: SubmissionsManager) {
    descriptionText.append("<a ${StyleManager().textStyleHeader};color:#${ColorUtil.toHex(EduColors.hyperlinkColor)}" +
                           " href=>${EduCoreBundle.message("submissions.login", submissionsManager.getPlatformName())}" +
                           "</a><a ${StyleManager().textStyleHeader}>")
    addHyperlinkListener(HyperlinkListener { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        submissionsManager.doAuthorize()
      }
    })
  }

  private fun getSubmissionsListener(project: Project,
                                     submissionsManager: SubmissionsManager,
                                     task: Task): HyperlinkListener {
    return HyperlinkListener { e ->
      if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        val submission = submissionsManager.getSubmission(task.id, e.description.toInt())
                         ?: return@HyperlinkListener
        val reply = submission.reply ?: return@HyperlinkListener
        runInEdt {
          showDiff(project, task, reply)
        }
      }
    }
  }

  private fun getImageUrl(status: String?): URL? {
    val icon = when (status) {
      EduNames.CORRECT -> if (StyleResourcesManager.isHighContrast()) EducationalCoreIcons.TaskSolvedNoFrameHighContrast else EducationalCoreIcons.TaskSolvedNoFrame
      else -> if (StyleResourcesManager.isHighContrast()) EducationalCoreIcons.TaskFailedNoFrameHighContrast else EducationalCoreIcons.TaskFailedNoFrame
    }
    return (icon as IconLoader.CachedImageIcon).url
  }

  private fun getLinkColor(submission: Submission): String {
    return when (submission.status) {
      EduNames.CORRECT -> getCorrectLinkColor()
      else -> getWrongLinkColor()
    }
  }

  private fun getCorrectLinkColor(): String {
    return if (StyleResourcesManager.isHighContrast()) {
      TaskDescriptionBundle.value("correct.label.foreground.high.contrast")
    }
    else {
      "#${ColorUtil.toHex(EduColors.correctLabelForeground)}"
    }
  }

  private fun getWrongLinkColor(): String {
    return if (StyleResourcesManager.isHighContrast()) {
      TaskDescriptionBundle.value("wrong.label.foreground.high.contrast")
    }
    else {
      "#${ColorUtil.toHex(EduColors.wrongLabelForeground)}"
    }
  }

  private fun getSubmissionTexts(reply: Reply, taskName: String): Map<String, String>? {
    val solutions = reply.solution
    if (solutions == null) {
      val submissionText = reply.code ?: return null
      return mapOf(taskName to submissionText)
    }
    return solutions.stream().collect(Collectors.toMap(SolutionFile::name, SolutionFile::text))
  }

  private fun formatDate(time: Date): String {
    val calendar = GregorianCalendar()
    calendar.time = time
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault())
    return formatter.format(calendar.time)
  }

  private fun addSubmissionsToText(submissionsNext: List<Submission>,
                                   descriptionText: StringBuilder) {
    descriptionText.append("<ul style=list-style-type:none;margin:0;padding:0;>")
    for (submission in submissionsNext) {
      descriptionText.append(submissionLink(submission))
    }
    descriptionText.append("</ul>")
  }

  private fun showDiff(project: Project, task: Task, reply: Reply) {
    val taskFiles = task.taskFiles.values.toMutableList()
    val submissionTexts = getSubmissionTexts(reply, task.name) ?: return
    val submissionTaskFiles = taskFiles.filter { it.isVisible && !it.isTestFile }
    val requests = submissionTaskFiles.mapNotNull {
      val virtualFile = it.getVirtualFile(project) ?: error("VirtualFile for ${it.name} not found")
      val currentFileContent = DiffContentFactory.getInstance().create(virtualFile.document.text, virtualFile.fileType)
      val submissionText = submissionTexts[it.name] ?: submissionTexts[task.name]
      if (submissionText == null) {
        null
      }
      else {
        val submissionFileContent = DiffContentFactory.getInstance().create(
          StepikSolutionsLoader.removeAllTags(submissionText),
          virtualFile.fileType)
        SimpleDiffRequest(EduCoreBundle.message("submissions.compare"),
                          currentFileContent,
                          submissionFileContent,
                          EduCoreBundle.message("submissions.local"),
                          EduCoreBundle.message("submissions.submission"))
      }
    }
    DiffManager.getInstance().showDiff(project, SimpleDiffRequestChain(requests), DiffDialogHints.FRAME)
  }

  private fun submissionLink(submission: Submission): String? {
    val time = submission.time ?: return null
    val pictureSize = (StyleManager().bodyFontSize * 0.75).roundToInt()
    val text = formatDate(time)
    return "<li><h><img src=${getImageUrl(submission.status)} hspace=6 width=${pictureSize} height=${pictureSize}/></h>" +
           "<a ${StyleManager().textStyleHeader};color:${getLinkColor(submission)} href=${submission.id}> ${text}</a></li>"
  }
}