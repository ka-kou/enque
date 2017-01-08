package jjug.questionnaire.enums;

import java.util.Comparator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Difficulty implements Comparator<Difficulty> {
	VERY_HARD(5), HARD(4), MODERATE(3), EASY(2), VERY_EASY(1);
	private final int value;

	@Override
	public int compare(Difficulty o1, Difficulty o2) {
		return Integer.compare(o1.getValue(), o2.getValue());
	}
}