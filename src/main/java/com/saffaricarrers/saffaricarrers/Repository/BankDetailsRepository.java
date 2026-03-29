package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.BankDetails;
import com.saffaricarrers.saffaricarrers.Entity.CarrierProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankDetailsRepository extends JpaRepository<BankDetails, Long> {

    Optional<BankDetails> findByCarrierProfile(CarrierProfile carrierProfile);

    Optional<BankDetails> findByCarrierProfile_UserUid(String userId);

    boolean existsByCarrierProfile(CarrierProfile carrierProfile);

    boolean existsByAccountNumberAndCarrierProfileNot(String accountNumber, CarrierProfile carrierProfile);

    // Admin queries
    List<BankDetails> findByVerificationStatusOrderByCreatedAtAsc(BankDetails.VerificationStatus status);

    List<BankDetails> findAllByOrderByCreatedAtDesc();
}