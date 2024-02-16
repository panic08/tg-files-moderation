package ru.marthastudios.tgsteamcookiegetter.pojo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class IgnoreWithdrawalBase {
    private long userChatId;
    private int firstMessageId;
    private int secondMessageId;
    private long withdrawalId;

}
