package com.wydad.digital.sports.repository;

import com.wydad.digital.sports.model.AcademyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademyMemberRepository extends JpaRepository<AcademyMember, Long> {
    List<AcademyMember> findByParentUserId(Long parentUserId);
}
