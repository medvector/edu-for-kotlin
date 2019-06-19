package com.jetbrains.edu.python.learning

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils
import com.jetbrains.edu.learning.EduCourseBuilder
import com.jetbrains.edu.learning.checker.TaskCheckerProvider
import com.jetbrains.edu.learning.configuration.EduConfigurator
import com.jetbrains.edu.python.learning.PyConfigurator.TASK_PY
import com.jetbrains.edu.python.learning.checker.PyNewTaskCheckerProvider
import com.jetbrains.python.newProject.PyNewProjectSettings
import icons.PythonIcons
import javax.swing.Icon

open class PyNewConfigurator : EduConfigurator<PyNewProjectSettings> {
  private val courseBuilder: PyNewCourseBuilder = PyNewCourseBuilder()

  override fun getCourseBuilder(): EduCourseBuilder<PyNewProjectSettings> = courseBuilder

  override fun getTestFileName(): String = TEST_FILE_NAME
  override fun getMockFileName(text: String): String = TASK_PY
  override fun getTestDirs(): List<String> = listOf(TEST_FOLDER)

  override fun excludeFromArchive(project: Project, file: VirtualFile): Boolean {
    val path = file.path
    return super.excludeFromArchive(project, file) || path.contains("__pycache__") || path.endsWith(".pyc")
  }

  override fun isEnabled(): Boolean = !(PlatformUtils.isPyCharm() || PlatformUtils.isCLion())
  override fun getTaskCheckerProvider(): TaskCheckerProvider = PyNewTaskCheckerProvider()
  override fun getLogo(): Icon = PythonIcons.Python.Python

  companion object {
    const val TEST_FILE_NAME = "test_task.py"
    const val TEST_FOLDER = "tests"
  }
}