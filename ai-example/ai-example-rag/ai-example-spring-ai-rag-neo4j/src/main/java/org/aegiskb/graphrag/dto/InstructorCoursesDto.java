package org.aegiskb.graphrag.dto;

import java.util.List;

public record InstructorCoursesDto(String instructor, List<String> otherCourses) {
}
