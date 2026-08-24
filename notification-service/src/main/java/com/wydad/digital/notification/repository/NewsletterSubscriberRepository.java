package com.wydad.digital.notification.repository;

import com.wydad.digital.notification.model.NewsletterSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsletterSubscriberRepository extends JpaRepository<NewsletterSubscriber, Long> {

    Optional<NewsletterSubscriber> findByEmailIgnoreCase(String email);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String token);
}
