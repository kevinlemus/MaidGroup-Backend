package com.maidgroup.maidgroup.service;

import com.maidgroup.maidgroup.dao.PasswordRepository;
import com.maidgroup.maidgroup.dao.UserRepository;
import com.maidgroup.maidgroup.model.User;
import com.maidgroup.maidgroup.model.userinfo.Gender;
import com.maidgroup.maidgroup.model.userinfo.Role;
import com.maidgroup.maidgroup.security.Password;
import com.maidgroup.maidgroup.service.exceptions.InvalidPasswordException;
import com.maidgroup.maidgroup.service.exceptions.InvalidTokenException;
import com.maidgroup.maidgroup.service.exceptions.UserNotFoundException;
import com.maidgroup.maidgroup.service.impl.UserServiceImpl;
import com.maidgroup.maidgroup.util.dto.Requests.ForgotPasswordRequest;
import com.maidgroup.maidgroup.util.dto.Requests.ResetPasswordRequest;
import com.maidgroup.maidgroup.util.dto.Responses.UserResponse;
import com.maidgroup.maidgroup.util.tokens.JWTUtility;
import jakarta.validation.constraints.Email;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.maidgroup.maidgroup.model.userinfo.Gender.FEMALE;
import static com.maidgroup.maidgroup.model.userinfo.Gender.MALE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserServiceTestSuite {
    @InjectMocks
    UserServiceImpl sut;
    @Mock
    UserRepository userRepository;
    @Mock
    BCryptPasswordEncoder passwordEncoder;
    // Create a mock instance of the JwtUtility class
    @Mock
    JWTUtility jwtUtility;
    @Mock
    EmailService emailService;

    @Before
    public void testPrep() {
        MockitoAnnotations.initMocks(this);
        // Set the value of the jwtUtility field on the UserServiceImpl object
        ReflectionTestUtils.setField(sut, "jwtUtility", jwtUtility);
    }

    @Test
    public void registerUser_returnsNewUser_givenValidUser() {
        // Arrange
        String password = "Missypfoo20!";

        User newUser = new User();
        newUser.setUsername("missypfoo");
        newUser.setRawPassword(password);
        newUser.setConfirmPassword(new Password(password));
        newUser.setFirstName("missy");
        newUser.setLastName("foo");
        newUser.setEmail("missyfoo@cats.com");
        newUser.setGender(FEMALE);
        newUser.setDateOfBirth(LocalDate.of(2020, 9, 15));

        when(userRepository.findByUsername(any(String.class))).thenReturn(null);
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // Act
        User user = sut.register(newUser);

        // Assert
        verify(userRepository, times(1)).save(any(User.class));
        assertNotNull(user);
        assertEquals(newUser, user);
    }


    @Test
    public void loginUser_returnsSuccessfulLogin_givenExistingUser() {
        // Arrange
        String username = "missypfoo";
        String rawPassword = "Missypfoo20!";
        String hashedPassword = passwordEncoder.encode(rawPassword);
        User existingUser = new User();
        existingUser.setUsername(username);
        existingUser.setPassword(new Password(hashedPassword));

        // Act
        when(userRepository.findByUsername(username)).thenReturn(existingUser);
        when(passwordEncoder.matches(rawPassword, hashedPassword)).thenReturn(true);
        User user = sut.login(username, rawPassword);

        // Assert
        verify(userRepository).findByUsername(username);
        Assert.assertNotNull(user);
    }

    @Test
    public void login_reactivatesUser_whenUserIsDeactivated() {
        // Arrange
        String username = "missypfoo";
        String password = "Missypfoo20!";
        User deactivatedUser = new User();
        deactivatedUser.setUsername(username);
        deactivatedUser.setPassword(new Password(passwordEncoder.encode(password)));
        deactivatedUser.setDeactivationDate(LocalDate.now());

        when(userRepository.findByUsername(username)).thenReturn(deactivatedUser);
        when(passwordEncoder.matches(password, deactivatedUser.getPassword().getHashedPassword())).thenReturn(true);

        // Act
        User user = sut.login(username, password);

        // Assert
        assertNull(user.getDeactivationDate());
        verify(userRepository, times(1)).save(user);
    }
    @Test
    public void updateUser_returnsSuccessfulUpdate_givenExistingUserUpdates() {
        // Arrange
        String oldRawPassword = "OldPassword123!";
        Password oldPassword = new Password(passwordEncoder.encode(oldRawPassword));

        User existingUser = new User();
        existingUser.setUserId(1L);  // Set a user ID
        existingUser.setUsername("oldUsername");
        existingUser.setPassword(oldPassword);
        existingUser.setRawPassword(oldRawPassword);
        existingUser.setConfirmPassword(oldPassword);
        existingUser.setFirstName("missy");
        existingUser.setLastName("foo");
        existingUser.setEmail("missyfoo@cats.com");
        existingUser.setGender(FEMALE);
        existingUser.setDateOfBirth(LocalDate.of(2020, 9, 15));
        existingUser.setRole(Role.USER);

        String newRawPassword = "NewPassword123!";
        Password newPassword = new Password(newRawPassword);

        User updatedUser = new User();
        updatedUser.setUserId(1L);  // Set the same user ID
        updatedUser.setUsername("newUsername");
        updatedUser.setPassword(newPassword);
        updatedUser.setRawPassword(newRawPassword);
        updatedUser.setConfirmPassword(newPassword);
        updatedUser.setFirstName("newMissy");
        updatedUser.setLastName("newFoo");
        updatedUser.setGender(FEMALE);
        updatedUser.setDateOfBirth(LocalDate.of(2020, 9, 15));


        when(userRepository.findById(any(Long.class))).thenReturn(Optional.of(existingUser));
        when(userRepository.findByUsername(any(String.class))).thenReturn(null);
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        // Act
        User user = sut.updateUser(updatedUser);

        // Assert
        verify(userRepository, times(1)).findById(any(Long.class));
        verify(userRepository, times(1)).findByUsername(any(String.class));
        verify(userRepository, times(1)).save(any(User.class));
        assertNotNull(user);
        assertEquals(updatedUser.getFirstName(), user.getFirstName());
    }

    @Test
    public void getAllUsers_returnsListOfUsers_whenUserIsAdmin() {
        // Arrange
        User adminUser = new User();
        adminUser.setUserId(1L);
        adminUser.setRole(Role.ADMIN);
        adminUser.setUsername("admin");

        User user1 = new User();
        user1.setUserId(2L);
        user1.setUsername("user1");

        User user2 = new User();
        user2.setUserId(3L);
        user2.setUsername("user2");

        List<User> allUsers = Arrays.asList(user1, user2);

        when(userRepository.findByUsername(adminUser.getUsername())).thenReturn(adminUser);
        when(userRepository.findAll()).thenReturn(allUsers);
        when(sut.getAllUsers(adminUser, null, null, null, null, null)).thenReturn(allUsers);

        // Act
        List<User> userResponses = sut.getAllUsers(adminUser, null, null, null, null, null);

        // Assert
        assertNotNull(userResponses);
        assertEquals(allUsers.size(), userResponses.size());
        for (int i = 0; i < allUsers.size(); i++) {
            assertEquals(allUsers.get(i).getUserId(), userResponses.get(i).getUserId());
            assertEquals(allUsers.get(i).getUsername(), userResponses.get(i).getUsername());
            // Add more assertions for the other fields as needed
        }
    }

    @Test
    public void getByUsername_returnsUser_whenRequesterIsAdminOrUser() {
        // Arrange
        User adminUser = new User();
        adminUser.setRole(Role.ADMIN);
        adminUser.setUsername("admin");

        User normalUser = new User();
        normalUser.setRole(Role.USER);  // Set the Role for the normal user
        normalUser.setUsername("user");

        when(userRepository.findByUsername(adminUser.getUsername())).thenReturn(adminUser);
        when(userRepository.findByUsername(normalUser.getUsername())).thenReturn(normalUser);

        // Act and Assert
        // Test when requester is admin
        User returnedUser = sut.getByUsername(adminUser.getUsername(), adminUser);
        assertEquals(adminUser, returnedUser);

        // Test when requester is the user themselves
        returnedUser = sut.getByUsername(normalUser.getUsername(), normalUser);
        assertEquals(normalUser, returnedUser);
    }

    @Test
    public void delete_deletesUser_whenRequesterIsAdminOrUser() {
        // Arrange
        User adminUser = new User();
        adminUser.setRole(Role.ADMIN);
        adminUser.setUserId(1L);

        User normalUser = new User();
        normalUser.setRole(Role.USER);  // Set the Role for the normal user
        normalUser.setUserId(2L);

        when(userRepository.findById(adminUser.getUserId())).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(normalUser.getUserId())).thenReturn(Optional.of(normalUser));

        // Act and Assert
        // Test when requester is admin
        sut.delete(adminUser.getUserId(), adminUser);
        verify(userRepository, times(1)).delete(adminUser);

        // Test when requester is the user themselves
        sut.delete(normalUser.getUserId(), normalUser);
        verify(userRepository, times(1)).delete(normalUser);
    }

    @Test // Validate password test
    public void register_throwsInvalidPasswordException_whenPasswordDoesNotMeetRequirements() {
        // Arrange
        User user = new User();
        user.setUsername("testUser");  // Set a username
        user.setFirstName("testFirstName");  // Set a first name
        user.setLastName("testLastName");  // Set a last name
        user.setEmail("testEmail@example.com");  // Set a valid email
        user.setGender(MALE);  // Set a gender
        user.setRawPassword("invalid");  // Set an invalid password

        // Act and Assert
        assertThrows(InvalidPasswordException.class, () -> sut.register(user));
    }

    @Test
    public void deactivateAccount_deactivatesUser_whenUserExists() {
        // Arrange
        User existingUser = new User();
        existingUser.setUserId(1L);
        existingUser.setUsername("testUser");
        existingUser.setRole(Role.USER);

        when(userRepository.findById(existingUser.getUserId())).thenReturn(Optional.of(existingUser));
        when(userRepository.findByUsername(existingUser.getUsername())).thenReturn(existingUser);

        // Act
        sut.deactivateAccount(existingUser.getUserId(), existingUser);

        // Assert
        assertNotNull(existingUser.getDeactivationDate());
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test(expected = UserNotFoundException.class)
    public void deactivateAccount_throwsUserNotFoundException_whenUserDoesNotExist() {
        // Arrange
        Long nonExistentUserId = 1L;
        User authUser = new User();
        authUser.setUserId(2L);
        authUser.setUsername("authUser");
        authUser.setRole(Role.USER);

        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());
        when(userRepository.findByUsername(authUser.getUsername())).thenReturn(authUser);

        // Act
        sut.deactivateAccount(nonExistentUserId, authUser);
    }

    @Test
    public void forgotPassword_sendsEmail_whenUserExists() {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest("testUser");
        User existingUser = new User();
        Password password = new Password(); // Create a new Password
        existingUser.setPassword(password); // Set the Password for the User
        existingUser.setEmail("testUser@example.com");
        when(userRepository.findByEmailOrUsername(request.getEmailOrUsername())).thenReturn(existingUser);
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        // Act
        sut.forgotPassword(request);

        // Assert
        verify(emailService, times(1)).sendEmail(anyString(), anyString(), anyString());
    }


    @Test(expected = UserNotFoundException.class)
    public void forgotPassword_throwsUserNotFoundException_whenUserDoesNotExist() {
        // Arrange
        ForgotPasswordRequest request = new ForgotPasswordRequest("nonExistentUser");
        when(userRepository.findByEmailOrUsername(request.getEmailOrUsername())).thenReturn(null);

        // Act
        sut.forgotPassword(request);

        // Assert
        // No need to assert here as the expected exception is already defined in the test annotation
    }

    @Test
    public void resetPassword_resetsPassword_whenTokenIsValid() {
        // Arrange
        ResetPasswordRequest request = new ResetPasswordRequest("validToken", "newPassword", "newPassword");
        User existingUser = new User();
        Password password = new Password();
        password.setResetToken("validToken");
        existingUser.setPassword(password);
        when(userRepository.findByPassword_ResetToken(request.getToken())).thenReturn(existingUser);
        when(passwordEncoder.encode(request.getNewPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        // Act
        sut.resetPassword(request);

        // Assert
        assertNull(existingUser.getPassword().getResetToken());
        verify(userRepository, times(2)).save(existingUser);
    }

    @Test(expected = InvalidTokenException.class)
    public void resetPassword_throwsInvalidTokenException_whenTokenIsInvalid() {
        // Arrange
        ResetPasswordRequest request = new ResetPasswordRequest("invalidToken", "newPassword", "newPassword");
        when(userRepository.findByPassword_ResetToken(request.getToken())).thenReturn(null);

        // Act
        sut.resetPassword(request);

        // Assert
        // No need to assert here as the expected exception is already defined in the test annotation
    }

}
