package ru.marthastudios.tgsteamcookiegetter.pojo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OperationBase {
    private long firstChatId;
    private int firstMessageId;
    private long secondChatId;
    private int secondMessageId;
    private long otherUserId;
    private long requestId;

}
