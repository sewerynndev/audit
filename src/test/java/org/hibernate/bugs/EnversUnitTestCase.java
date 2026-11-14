package org.hibernate.bugs;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.Audited;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DomainModel(annotatedClasses = {EnversUnitTestCase.Item.class, EnversUnitTestCase.Tag.class})
@ServiceRegistry(settings = {
        @Setting(name = AvailableSettings.SHOW_SQL, value = "true"),
        @Setting(name = AvailableSettings.FORMAT_SQL, value = "true")
})
@SessionFactory
class EnversUnitTestCase {

    @Test
    void removingOneElementIsVisibleInHistory(SessionFactoryScope scope) {
        createItemWithTwoTags(scope, 1L, 10L, 11L);
        updateItem(scope, 1L, item -> item.tags.removeIf(tag -> tag.id == 10L));
        assertEquals(Set.of(11L), tagIdsAtLastRevision(scope, 1L));
    }

    @Test
    void clearingWholeCollectionIsVisibleInHistory(SessionFactoryScope scope) {
        createItemWithTwoTags(scope, 2L, 20L, 21L);
        updateItem(scope, 2L, item -> item.tags.clear());
        assertEquals(Set.of(), tagIdsAtLastRevision(scope, 2L));
    }

    @Test
    void replacingWithEmptyCollectionIsVisibleInHistory(SessionFactoryScope scope) {
        createItemWithTwoTags(scope, 3L, 30L, 31L);
        updateItem(scope, 3L, item -> item.tags = new HashSet<>());
        assertEquals(Set.of(), tagIdsAtLastRevision(scope, 3L));
    }

    private static void createItemWithTwoTags(SessionFactoryScope scope, Long itemId, Long firstTagId, Long secondTagId) {
        scope.inTransaction(session -> {
            var first = new Tag(firstTagId);
            var second = new Tag(secondTagId);
            session.persist(first);
            session.persist(second);
            var item = new Item(itemId, "created");
            item.tags.add(first);
            item.tags.add(second);
            session.persist(item);
        });
    }

    private static void updateItem(SessionFactoryScope scope, Long itemId, Consumer<Item> change) {
        scope.inTransaction(session -> {
            var item = session.find(Item.class, itemId);
            item.name = "updated";
            change.accept(item);
        });
    }

    private static Set<Long> tagIdsAtLastRevision(SessionFactoryScope scope, Long itemId) {
        var ids = new HashSet<Long>();
        scope.inSession(session -> {
            var auditReader = AuditReaderFactory.get(session);
            var revisions = auditReader.getRevisions(Item.class, itemId);
            var audited = auditReader.find(Item.class, itemId, revisions.get(revisions.size() - 1));
            assertEquals("updated", audited.name);
            audited.tags.forEach(tag -> ids.add(tag.id));
        });
        return ids;
    }

    @Entity(name = "Item")
    @Audited
    static class Item {
        @Id
        Long id;

        String name;

        @ManyToMany
        Set<Tag> tags = new HashSet<>();

        Item() {
        }

        Item(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Entity(name = "Tag")
    @Audited
    static class Tag {
        @Id
        Long id;

        Tag() {
        }

        Tag(Long id) {
            this.id = id;
        }
    }
}
