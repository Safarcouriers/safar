package com.saffaricarrers.saffaricarrers.Services;

import com.saffaricarrers.saffaricarrers.Dtos.AddressRequest;
import com.saffaricarrers.saffaricarrers.Entity.Address;
import com.saffaricarrers.saffaricarrers.Entity.User;
import com.saffaricarrers.saffaricarrers.Exception.ResourceNotFoundException;
import com.saffaricarrers.saffaricarrers.Repository.AddressRepository;
import com.saffaricarrers.saffaricarrers.Repository.UserRepository;
import com.saffaricarrers.saffaricarrers.Responses.AddressResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    /**
     * ✅ FIXED: Evict profile cache when creating address
     */

    @CacheEvict(value = {"profiles", "users"}, key = "#userId")
    public AddressResponse createAddress(String userId, AddressRequest request) {
        log.info("Creating address and evicting cache for userId: {}", userId);
        System.out.println("✅ Creating address for userId: " + userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getIsDefault() != null && request.getIsDefault()) {
            addressRepository.unsetDefaultAddress(user);
        }

        Address address = new Address();
        address.setUser(user);
        address.setAddressType(Address.AddressType.valueOf(request.getAddressType().toUpperCase()));
        address.setFullName(request.getFullName());
        address.setAddress(request.getAddress());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPincode(request.getPincode());
        address.setMobile(request.getMobile());
        address.setLatitude(request.getLatitude());
        address.setLongitude(request.getLongitude());
        address.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);

        if (!addressRepository.existsByUserAndIsDefaultTrue(user)) {
            address.setIsDefault(true);
        }

        Address savedAddress = addressRepository.save(address);
        log.info("✅ Address saved and cache evicted for userId: {}", userId);
        System.out.println("✅ Address created successfully, cache evicted!");

        return mapToAddressResponse(savedAddress);
    }

    // No cache needed for read operations
    public List<AddressResponse> getUserAddresses(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return addressRepository.findByUserOrderByIsDefaultDescCreatedAtDesc(user)
                .stream()
                .map(this::mapToAddressResponse)
                .collect(Collectors.toList());
    }

    /**
     * ✅ FIXED: Evict profile cache when updating address
     */
    @CacheEvict(value = {"profiles", "users"}, key = "#userId")
    public AddressResponse updateAddress(String userId, Long addressId, AddressRequest request) {
        log.info("Updating address and evicting cache for userId: {}", userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Address does not belong to the user");
        }

        if (request.getIsDefault() != null && request.getIsDefault() && !address.getIsDefault()) {
            addressRepository.unsetDefaultAddress(user);
        }

        address.setAddressType(Address.AddressType.valueOf(request.getAddressType().toUpperCase()));
        address.setFullName(request.getFullName());
        address.setAddress(request.getAddress());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setCountry(request.getCountry());
        address.setPincode(request.getPincode());
        address.setMobile(request.getMobile());
        address.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : address.getIsDefault());

        Address updatedAddress = addressRepository.save(address);
        log.info("✅ Address updated and cache evicted for userId: {}", userId);

        return mapToAddressResponse(updatedAddress);
    }

    /**
     * ✅ FIXED: Evict profile cache when deleting address
     */
    @CacheEvict(value = {"profiles", "users"}, key = "#userId")
    public void deleteAddress(String userId, Long addressId) {
        log.info("Deleting address and evicting cache for userId: {}", userId);

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Address does not belong to the user");
        }

        if (address.getIsDefault()) {
            User user = userRepository.findByUserId(userId).orElseThrow();
            List<Address> otherAddresses = addressRepository.findByUserOrderByIsDefaultDescCreatedAtDesc(user);
            otherAddresses.removeIf(addr -> addr.getAddressId().equals(addressId));

            if (!otherAddresses.isEmpty()) {
                Address firstAddress = otherAddresses.get(0);
                firstAddress.setIsDefault(true);
                addressRepository.save(firstAddress);
            }
        }

        addressRepository.delete(address);
        log.info("✅ Address deleted and cache evicted for userId: {}", userId);
    }

    // No cache needed for read operations
    public AddressResponse getDefaultAddress(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Address defaultAddress = addressRepository.findByUserAndIsDefaultTrue(user)
                .orElseThrow(() -> new ResourceNotFoundException("No default address found"));

        return mapToAddressResponse(defaultAddress);
    }

    /**
     * ✅ FIXED: Evict profile cache when setting default address
     */
    @CacheEvict(value = {"profiles", "users"}, key = "#userId")
    public AddressResponse setDefaultAddress(String userId, Long addressId) {
        log.info("Setting default address and evicting cache for userId: {}", userId);

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (!address.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("Address does not belong to the user");
        }

        addressRepository.unsetDefaultAddress(user);

        address.setIsDefault(true);
        Address savedAddress = addressRepository.save(address);

        log.info("✅ Default address set and cache evicted for userId: {}", userId);

        return mapToAddressResponse(savedAddress);
    }

    private AddressResponse mapToAddressResponse(Address address) {
        AddressResponse response = new AddressResponse();
        response.setAddressId(address.getAddressId());
        response.setAddressType(address.getAddressType().name());
        response.setFullName(address.getFullName());
        response.setAddress(address.getAddress());
        response.setCity(address.getCity());
        response.setState(address.getState());
        response.setCountry(address.getCountry());
        response.setPincode(address.getPincode());
        response.setMobile(address.getMobile());
        response.setIsDefault(address.getIsDefault());
        response.setCreatedAt(address.getCreatedAt());
        return response;
    }
}
