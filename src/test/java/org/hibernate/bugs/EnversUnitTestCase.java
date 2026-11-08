package org.hibernate.bugs;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.Audited;
import org.hibernate.envers.configuration.EnversSettings;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DomainModel(annotatedClasses = {EnversUnitTestCase.Item.class, EnversUnitTestCase.Tag.class})
@ServiceRegistry(settings = {
        @Setting(name = AvailableSettings.SHOW_SQL, value = "true"),
        @Setting(name = AvailableSettings.FORMAT_SQL, value = "true"),
        @Setting(name = EnversSettings.STORE_DATA_AT_DELETE, value = "true")
})
@SessionFactory
class EnversUnitTestCase {

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

        scope.inSession(session -> {
            var auditReader = AuditReaderFactory.get(session);
            var deleteRevision = auditReader.getRevisions(Item.class, 1L).get(1);
            var deleted = (Item) auditReader.createQuery()
                    .forEntitiesAtRevision(Item.class, Item.class.getName(), deleteRevision, true)
                    .add(AuditEntity.id().eq(1L))
                    .getSingleResult();

            assertAll(
                    () -> assertEquals(Set.of("a"), deleted.labels),
                    () -> assertEquals(1, deleted.tags.size())
            );
        });
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
