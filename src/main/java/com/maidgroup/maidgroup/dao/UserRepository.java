package com.maidgroup.maidgroup.dao;

import com.maidgroup.maidgroup.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select u from User u where u.username = :username")
    User findByUsername(@Param("username") String username);

    @Query("select u from User u where u.deactivationDate < :date")
    List<User> findAllByDeactivationDateBefore(@Param("date") LocalDate date);
    User findByPassword_ResetToken(String resetToken);
    @Query("select u from User u where u.email = :emailOrUsername or u.username = :emailOrUsername")
    User findByEmailOrUsername(@Param("emailOrUsername") String emailOrUsername);
    User findByEmail(String email);
}

