@file:Suppress("unused")

package com.jetbrains.edu.learning.stepik.api

import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.courseFormat.Lesson
import com.jetbrains.edu.learning.courseFormat.Section
import com.jetbrains.edu.learning.stepik.StepikUserInfo
import com.jetbrains.edu.learning.stepik.StepikWrappers
import retrofit2.Call
import retrofit2.http.*

interface StepikService {
  @GET("stepics/1/")
  fun getCurrentUser(): Call<UsersList>

  @POST("enrollments")
  fun enrollments(@Body enrollment: EnrollmentData): Call<Any>

  @GET("enrollments/{id}/")
  fun enrollments(@Path("id") courseId: Int): Call<Any>

  @GET("courses")
  fun courses(@Query("is_idea_compatible") isIdeaCompatible: Boolean,
              @Query("is_public") isPublic: Boolean,
              @Query("page") page: Int,
              @Query("enrolled") enrolled: Boolean?): Call<CoursesList>

  @GET("courses/{id}")
  fun courses(@Path("id") courseId: Int,
              @Query("is_idea_compatible") isIdeaCompatible: Boolean?): Call<CoursesList>

  @GET("users")
  fun users(@Query("ids[]") vararg ids: Int): Call<UsersList>

  @GET("sections")
  fun sections(@Query("ids[]") vararg ids: Int): Call<SectionsList>

  @GET("sections/{id}")
  fun sections(@Path("id") sectionId: Int): Call<SectionsList>

  @GET("lessons/{id}")
  fun lessons(@Path("id") lessonId: Int): Call<LessonsList>

  @GET("lessons")
  fun lessons(@Query("ids[]") vararg ids: Int): Call<LessonsList>

  @GET("units")
  fun units(@Query("ids[]") vararg ids: Int): Call<UnitsList>
}

class UsersList {
  lateinit var meta: Any
  lateinit var users: List<StepikUserInfo>
}

class Enrollment(var course: String)

class EnrollmentData(courseId: Int) {
  var enrollment: Enrollment = Enrollment(courseId.toString())
}

class CoursesList {
  lateinit var meta: Map<Any, Any>
  lateinit var courses: MutableList<EduCourse>
}

class SectionsList {
  lateinit var sections: List<Section>
}

class LessonsList {
  lateinit var lessons: List<Lesson>
}

class UnitsList {
  lateinit var units: List<StepikWrappers.Unit>
}