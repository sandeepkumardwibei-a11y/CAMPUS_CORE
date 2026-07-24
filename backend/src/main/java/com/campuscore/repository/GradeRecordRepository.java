package com.campuscore.repository;

import com.campuscore.entity.GradeRecord;
import com.campuscore.entity.GradeRecord.GradeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeRecordRepository extends JpaRepository<GradeRecord, Long> {
    Optional<GradeRecord> findByExamExamIdAndStudentUserId(Long examId, Long studentId);
    List<GradeRecord> findByStudentUserId(Long studentId);
    List<GradeRecord> findByExamExamId(Long examId);
    List<GradeRecord> findByExamExamIdAndStatus(Long examId, GradeStatus status);
}
