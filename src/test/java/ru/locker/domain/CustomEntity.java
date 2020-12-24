package ru.locker.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class CustomEntity implements Lockable<Integer> {

    private Integer id;
    private Integer payload;

}
