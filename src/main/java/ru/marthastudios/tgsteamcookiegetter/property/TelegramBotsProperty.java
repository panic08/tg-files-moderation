package ru.marthastudios.tgsteamcookiegetter.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class TelegramBotsProperty {
    @Value("${telegram.bots.token}")
    private String token;
    @Value("${telegram.bots.name}")
    private String name;
}
