package com.longtoast.bilbil_api.repository;

import com.longtoast.bilbil_api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Integer> {
    //dd
    Optional<User> findByNickname(String nickname);
    Optional<User> findByEmail(String email);
    Optional<User> findByProfileImageUrl(String profileImageUrl);
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByCreditScore(Integer creditScore);
    Optional<User> findByAddress(String address);


    Optional<User> findByLocationLatitude(double location_latitude);
    Optional<User> findByLocationLongitude(double location_longitude);

    Optional<User> findById(Integer id);
}
