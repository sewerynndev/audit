package org.hibernate.bugs;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Version;
import org.hibernate.annotations.Audited;
import org.hibernate.audit.AuditLogFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DomainModel(annotatedClasses = {ORMUnitTestCase.Account.class})
@ServiceRegistry(settings = {
        @Setting(name = AvailableSettings.SHOW_SQL, value = "true"),
        @Setting(name = AvailableSettings.FORMAT_SQL, value = "true")
})
@SessionFactory
class ORMUnitTestCase {

    @Test
    void changingAuditedAttributeBumpsVersion(SessionFactoryScope scope) {
        scope.inTransaction(session -> session.persist(new Account(1L, "NEW", "secret-1")));
        scope.inTransaction(session -> session.find(Account.class, 1L).status = "ACTIVE");
        scope.inTransaction(session -> assertEquals(1, session.find(Account.class, 1L).version));
    }

    @Test
    void changingExcludedAttributeBumpsVersion(SessionFactoryScope scope) {
        scope.inTransaction(session -> session.persist(new Account(2L, "NEW", "secret-1")));
        scope.inTransaction(session -> session.find(Account.class, 2L).secret = "secret-2");
        scope.inTransaction(session -> {
            var account = session.find(Account.class, 2L);
            assertEquals("secret-2", account.secret);
            assertEquals(1, account.version, "update of an @Audited.Excluded attribute did not increment @Version");
        });
    }

    @Test
    void concurrentChangesToAuditedAttributeConflict(SessionFactoryScope scope) {
        scope.inTransaction(session -> session.persist(new Account(3L, "NEW", "secret-1")));
        assertThrows(OptimisticLockException.class, () -> concurrentUpdate(scope, 3L, account -> account.status = "A", account -> account.status = "B"));
    }

    @Test
    void concurrentChangesToExcludedAttributeConflict(SessionFactoryScope scope) {
        scope.inTransaction(session -> session.persist(new Account(4L, "NEW", "secret-1")));
        assertThrows(OptimisticLockException.class,
                () -> concurrentUpdate(scope, 4L, account -> account.secret = "from-a", account -> account.secret = "from-b"),
                "second writer silently overwrote the first one (lost update)");
    }

    @Test
    void excludedAttributeInsideEmbeddableIsNotAudited(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var account = new Account(5L, "NEW", "secret-1");
            account.invite = new Invite("invite-token", "2030-01-01");
            session.persist(account);
        });

        try (var auditLog = AuditLogFactory.create(scope.getSessionFactory())) {
            var changeset = auditLog.getChangesets(Account.class, 5L).get(0);
            var audited = auditLog.find(Account.class, 5L, changeset);

            assertAll(
                    () -> assertNull(audited.secret, "top-level @Audited.Excluded attribute"),
                    () -> assertNull(audited.invite.token, "@Audited.Excluded attribute inside @Embeddable")
            );
        }
    }

    private static void concurrentUpdate(SessionFactoryScope scope, Long id, java.util.function.Consumer<Account> first, java.util.function.Consumer<Account> second) {
        try (var a = scope.getSessionFactory().openSession(); var b = scope.getSessionFactory().openSession()) {
            a.beginTransaction();
            b.beginTransaction();
            var accountA = a.find(Account.class, id);
            var accountB = b.find(Account.class, id);
            first.accept(accountA);
            a.getTransaction().commit();
            second.accept(accountB);
            b.getTransaction().commit();
        }
    }

    @Entity(name = "Account")
    @Audited
    static class Account {
        @Id
        Long id;

        @Version
        int version;

        String status;

        @Audited.Excluded
        String secret;

        @Embedded
        Invite invite;

        Account() {
        }

        Account(Long id, String status, String secret) {
            this.id = id;
            this.status = status;
            this.secret = secret;
        }
    }

    @Embeddable
    static class Invite {
        @Audited.Excluded
        String token;

        String expiresAt;

        Invite() {
        }

        Invite(String token, String expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
