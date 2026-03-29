package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByMobile(String mobile);
    boolean existsByEmail(String email);
    boolean existsByMobile(String mobile);
    boolean existsByUserId(String userId);

    @EntityGraph(value = "User.basic", type = EntityGraph.EntityGraphType.LOAD)
    Optional<User> findByUserId(String uid);

    @EntityGraph(value = "User.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    @Override
    List<User> findAll();

    @EntityGraph(value = "User.withCarrierProfile", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT u FROM User u WHERE u.userId = :userId")
    Optional<User> findByUserIdWithProfile(@Param("userId") String userId);

    // ✅ FIXED — removed LEFT JOIN FETCH cp.bankDetails
    @Query(value = "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.carrierProfile cp",
            countQuery = "SELECT COUNT(DISTINCT u) FROM User u")
    Page<User> findAllWithCarrierProfile(Pageable pageable);

    Page<User> findAll(Pageable pageable);

    // ✅ NEW — direct FCM update, zero joins
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.FcmToken = :token WHERE u.userId = :userId")
    int updateFcmTokenDirectly(
            @Param("userId") String userId,
            @Param("token") String token
    );
    // Count queries (replace findAll() + stream filtering)
    long countByStatus(User.UserStatus status);
    long countByUserType(User.UserType userType);
    long countByVerificationStatus(User.VerificationStatus verificationStatus);

    @Query("SELECT u.gender, COUNT(u) FROM User u GROUP BY u.gender")
    List<Object[]> countGroupByGender();



    // Date range queries (replace findAll() + filter by createdAt)
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT CAST(u.createdAt AS date), COUNT(u) FROM User u " +
            "WHERE u.createdAt BETWEEN :from AND :to GROUP BY CAST(u.createdAt AS date)")
    List<Object[]> countGroupByCreatedDate(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // findByVerified — for list endpoints (replaces findAll() + stream filter)




    // findByVerified — was failing because field is isVerified not verified
    @Query("SELECT u FROM User u WHERE u.isVerified = :v")
    List<User> findByVerified(@Param("v") boolean v);

    // countByVerified — same issue
    @Query("SELECT COUNT(u) FROM User u WHERE u.isVerified = :v")
    long countByVerified(@Param("v") boolean v);

    // countVerifiedWithIncompleteProfile — same issue + also uses FcmToken field
    @Query("SELECT COUNT(u) FROM User u WHERE u.isVerified = true " +
            "AND (u.profileUrl IS NULL OR u.aadharFrontUrl IS NULL " +
            "OR u.aadharBackUrl IS NULL OR u.panCardUrl IS NULL)")
    long countVerifiedWithIncompleteProfile();

    // findAllForBroadcast — fix FcmToken capital F
    @Query("SELECT u FROM User u WHERE u.FcmToken IS NOT NULL AND u.FcmToken <> ''")
    List<User> findAllForBroadcast();

    // findByUserTypeForBroadcast — fix FcmToken capital F
    @Query("SELECT u FROM User u WHERE u.userType = :userType AND u.FcmToken IS NOT NULL AND u.FcmToken <> ''")
    List<User> findByUserTypeForBroadcast(@Param("userType") User.UserType userType);
}
