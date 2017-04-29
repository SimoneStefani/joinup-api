package io.github.core55.authentication;

import java.util.UUID;
import java.io.IOException;
import java.util.Collections;
import io.github.core55.user.User;
import io.github.core55.email.EmailService;
import io.github.core55.core.StringResponse;
import io.github.core55.user.UserRepository;
import org.springframework.hateoas.Resource;
import java.security.GeneralSecurityException;
import io.github.core55.tokens.MagicLinkToken;
import com.google.api.client.json.JsonFactory;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import io.github.core55.email.MailContentBuilder;
import javax.persistence.EntityNotFoundException;
import org.springframework.web.bind.annotation.*;
import io.github.core55.tokens.MagicLinkTokenRepository;
import org.springframework.mail.javamail.JavaMailSender;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.springframework.beans.factory.annotation.Autowired;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

@RestController
@RequestMapping("api/login")
public class LoginController {

    private final JavaMailSender javaMailSender;
    private final UserRepository userRepository;
    private final MailContentBuilder mailContentBuilder;
    private final MagicLinkTokenRepository magicLinkTokenRepository;
    private static final JsonFactory jsonFactory = new JacksonFactory();
    private static final NetHttpTransport netHttpTransport = new NetHttpTransport();

    @Autowired
    public LoginController(JavaMailSender javaMailSender, MailContentBuilder mailContentBuilder, UserRepository userRepository, MagicLinkTokenRepository magicLinkTokenRepository) {
        this.javaMailSender = javaMailSender;
        this.mailContentBuilder = mailContentBuilder;
        this.userRepository = userRepository;
        this.magicLinkTokenRepository = magicLinkTokenRepository;
    }

    /**
     * Send an email with a magic link for authentication. The user provides an email and a random token is generated
     * and attached to the specific User entity. An email is then sent to the provided address with a special
     * authentication link containing the same token.
     */
    @RequestMapping(value = "/send", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public StringResponse sendLoginEmail(@RequestBody User user) throws EntityNotFoundException {

        String tokenValue = generateToken();
        User retrievedUser = userRepository.findByUsername(user.getUsername());

        if (retrievedUser == null) {
            throw new EntityNotFoundException("Couldn't find the user " + user.getUsername());
        }

        MagicLinkToken magicLinkToken = new MagicLinkToken(tokenValue, retrievedUser.getId());
        magicLinkTokenRepository.save(magicLinkToken);

        EmailService emailService = new EmailService(javaMailSender, mailContentBuilder);
        emailService.prepareAndSend(user.getUsername(), "Login in CuLater", "/api/login/" + tokenValue);

        return new StringResponse("Email sent correctly to " + user.getUsername());
    }

    /**
     * Authenticate a user with a magic link. Extract the token from the magic link and search a User entity with such
     * token. If found generate a JWT and attach it to the response header. Otherwise throw a EntityNotFoundException.
     */
    @RequestMapping(value = "/{token}", method = RequestMethod.GET, produces = "application/json")
    public @ResponseBody ResponseEntity<?> authenticateWithMagicLink(@PathVariable("token") String token, HttpServletResponse res)
            throws EntityNotFoundException {

        MagicLinkToken magicLinkToken = magicLinkTokenRepository.findByValue(token);
        if (magicLinkToken == null) {
            throw new EntityNotFoundException("This magic link is not valid!");
        }

        User user = userRepository.findOne(magicLinkToken.getUserId());
        if (user != null) {
            magicLinkTokenRepository.delete(magicLinkToken);

            TokenAuthenticationService.addAuthentication(res, user.getUsername());
            Resource<User> resource = new Resource<>(user);
            return ResponseEntity.ok(resource);
        } else {
            throw new EntityNotFoundException("Couldn't authenticate the user!");
        }
    }

    /**
     * Authenticate a user with Google sign-in token. It expects a Google IdToken from the front-end. It then verifies
     * the validity of the token and retrieve the user data from Google. Generate a JWT and attach it to the response
     * header.
     */
    @RequestMapping(value = "/token", method = RequestMethod.POST, consumes = "application/json", produces = "application/json")
    public @ResponseBody ResponseEntity<?> authenticateWithGoogleToken(@RequestBody GoogleToken googleToken, HttpServletResponse res)
            throws GeneralSecurityException, IOException, EntityNotFoundException{

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(netHttpTransport, jsonFactory)
                .setAudience(Collections.singletonList("64814529919-8a9dch2j1lhpsau1sql0htrm67h69ijn.apps.googleusercontent.com"))
                .build();

        GoogleIdToken idToken = verifier.verify(googleToken.getIdToken());
        if (idToken != null) {
            Payload payload = idToken.getPayload();
            String username = payload.getEmail();

            TokenAuthenticationService.addAuthentication(res, username);
            User user = userRepository.findByUsername(username);
            Resource<User> resource = new Resource<>(user);
            return ResponseEntity.ok(resource);
        } else {
            throw new EntityNotFoundException("Couldn't authenticate the user!");
        }

    }

    /**
     * Generate an authentication token which is composed by 64 random characters and the encrypted creation timestamp.
     *
     * @return the newly generated token
     */
    private String generateToken() {
        String initial = UUID.randomUUID().toString().replaceAll("-", "");
        String end = UUID.randomUUID().toString().replaceAll("-", "");

        return initial + end;
    }
}
