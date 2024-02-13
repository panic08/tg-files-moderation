package ru.marthastudios.tgsteamcookiegetter.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Pair <T, L> {
    private T firstValue;
    private L secondValue;
}
