package ru.spbstu.dto;

import ru.spbstu.model.Tag;
import ru.spbstu.model.User;

public record TagDto (
        Long id,
        String name,
        Long userId,
        Long telegramId
) {
    public static TagDto toDto(Tag tag) {
        return new TagDto(
                tag.getId(),
                tag.getName(),
                tag.getUser().getId(),
                tag.getUser().getTelegramId()
        );
    }

    public Tag toEntity(User user) {
        Tag tag = new Tag();
        tag.setName(this.name);
        tag.setUser(user);
        return tag;
    }
}
