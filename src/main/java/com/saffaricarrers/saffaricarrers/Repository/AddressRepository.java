package com.saffaricarrers.saffaricarrers.Repository;

import com.saffaricarrers.saffaricarrers.Entity.Address;
import com.saffaricarrers.saffaricarrers.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserOrderByIsDefaultDescCreatedAtDesc(User user);
    Optional<Address> findByUserAndIsDefaultTrue(User user);

    @Modifying
    @Transactional
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.user = :user")
    void unsetDefaultAddress(User user);
    boolean existsByUser(User user);

    boolean existsByUserAndIsDefaultTrue(User user);
}
