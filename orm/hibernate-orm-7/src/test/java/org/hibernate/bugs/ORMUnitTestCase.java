package org.hibernate.bugs;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import org.hibernate.annotations.Audited;
import org.hibernate.audit.AuditLogFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DomainModel(annotatedClasses = {ORMUnitTestCase.Item.class, ORMUnitTestCase.Tag.class})
@ServiceRegistry(settings = {
        @Setting(name = AvailableSettings.SHOW_SQL, value = "true"),
        @Setting(name = AvailableSettings.FORMAT_SQL, value = "true")
})
@SessionFactory
class ORMUnitTestCase {

    @Test
    void deletedRecordKeepsCollections(SessionFactoryScope scope) {
        scope.inTransaction(session -> {
            var tag = new Tag();
            tag.id = 1L;
            session.persist(tag);
            var item = new Item();
            item.id = 1L;
            item.labels.add("a");
            item.tags.add(tag);
            session.persist(item);
        });
        scope.inTransaction(session -> session.remove(session.find(Item.class, 1L)));

        try (var auditLog = AuditLogFactory.create(scope.getSessionFactory())) {
            var deleteChangeset = auditLog.getChangesets(Item.class, 1L).get(1);
            var deleted = auditLog.find(Item.class, 1L, deleteChangeset, true);

            assertEquals(Set.of("a"), deleted.labels);
            assertEquals(1, deleted.tags.size());
        }
    }

    @Entity(name = "Item")
    @Audited
    static class Item {
        @Id
        Long id;
        @ElementCollection
        Set<String> labels = new HashSet<>();
        @ManyToMany
        Set<Tag> tags = new HashSet<>();
    }

    @Entity(name = "Tag")
    @Audited
    static class Tag {
        @Id
        Long id;
    }
}
