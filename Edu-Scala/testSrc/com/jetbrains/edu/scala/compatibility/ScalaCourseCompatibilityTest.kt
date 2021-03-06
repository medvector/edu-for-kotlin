package com.jetbrains.edu.scala.compatibility

import com.jetbrains.edu.learning.compatibility.CourseCompatibilityProviderTestBase
import com.jetbrains.edu.learning.compatibility.ScalaGradleCourseCompatibilityProvider
import com.jetbrains.edu.learning.compatibility.ScalaSbtCourseCompatibilityProvider

class ScalaGradleCourseCompatibilityProviderTest : CourseCompatibilityProviderTestBase(ScalaGradleCourseCompatibilityProvider::class)
class ScalaSbtCourseCompatibilityProviderTest : CourseCompatibilityProviderTestBase(ScalaSbtCourseCompatibilityProvider::class)