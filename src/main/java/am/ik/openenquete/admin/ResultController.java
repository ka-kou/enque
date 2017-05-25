package am.ik.openenquete.admin;

import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

import am.ik.openenquete.questionnaire.enums.Difficulty;
import am.ik.openenquete.questionnaire.enums.Satisfaction;
import am.ik.openenquete.seminar.ResponseForSeminar;
import am.ik.openenquete.seminar.ResponseForSeminarRepository;
import am.ik.openenquete.seminar.Seminar;
import am.ik.openenquete.seminar.SeminarRepository;
import am.ik.openenquete.session.ResponseForSession;
import am.ik.openenquete.session.ResponseForSessionRepository;
import am.ik.openenquete.session.Session;
import am.ik.openenquete.session.SessionRepository;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ResultController {
	private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
	private final SeminarRepository seminarRepository;
	private final SessionRepository sessionRepository;
	private final ResponseForSeminarRepository responseForSeminarRepository;
	private final ResponseForSessionRepository responseForSessionRepository;
	private final MessageSource messageSource;
	private final ObjectMapper objectMapper;

	@GetMapping("admin/seminars/{seminarId}/result")
	String seminarForAdmin(@PathVariable UUID seminarId, Model model, Locale locale)
			throws IOException {
		List<ResponseForSeminar> responses = responseForSeminarRepository
				.findBySeminar_SeminarId(seminarId);
		Seminar seminar = seminarRepository.findOne(seminarId).get();
		Map<String, Long> satisfactions = satisfactionMap(
				responses.stream().collect(
						groupingBy(ResponseForSeminar::getSatisfaction, counting())),
				locale);

		List<String> comments = responses.stream() //
				.sorted(comparing(ResponseForSeminar::getCreatedAt)) //
				.map(ResponseForSeminar::getComment) //
				.filter(s -> !StringUtils.isEmpty(s)) //
				.collect(toList());
		List<String> requests = responses.stream() //
				.sorted(comparing(ResponseForSeminar::getCreatedAt)) //
				.map(ResponseForSeminar::getRequest) //
				.filter(s -> !StringUtils.isEmpty(s)) //
				.collect(toList());

		List<ResponseForSession> responseForSessions = responseForSessionRepository
				.findBySession_Seminar_SeminarId(seminarId);
		Map<LocalDateTime, Long> countsForSessions = countsForSession(
				responseForSessions);

		countsForSessions.forEach((k, v) -> {
			System.out.println(k + "\t" + v);
		});

		Map<LocalDateTime, Long> countsForSeminar = countsForSeminar(responses);

		countsForSeminar.forEach((k, v) -> {
			System.out.println(k + "\t" + v);
		});

		model.addAttribute("seminar", seminar);
		model.addAttribute("satisfactions", satisfactions);
		model.addAttribute("satisfactionsArray",
				arrayJsonString(satisfactions, "Satisfaction"));
		model.addAttribute("comments", comments);
		model.addAttribute("requests", requests);

		model.addAttribute("countsForSeminar", arrayJsonString(countsForSeminar, "Time"));
		model.addAttribute("countsForSessions",
				arrayJsonString(countsForSessions, "Time"));
		return "admin/seminar";
	}

	@GetMapping({ "admin/sessions/{sessionId}/result", "speaker/sessions/{sessionId}" })
	String sessionForAdmin(@PathVariable UUID sessionId, Model model, Locale locale)
			throws IOException {
		List<ResponseForSession> responses = responseForSessionRepository
				.findBySession_SessionId(sessionId);
		Session session = sessionRepository.findOne(sessionId).get();
		Map<String, Long> satisfactions = satisfactionMap(
				responses.stream().collect(
						groupingBy(ResponseForSession::getSatisfaction, counting())),
				locale);
		Map<String, Long> difficulties = difficultyMap(
				responses.stream().collect(
						groupingBy(ResponseForSession::getDifficulty, counting())),
				locale);
		List<String> comments = responses.stream() //
				.sorted(comparing(ResponseForSession::getCreatedAt)) //
				.map(ResponseForSession::getComment) //
				.filter(s -> !StringUtils.isEmpty(s)) //
				.collect(toList());

		model.addAttribute("s", session);
		model.addAttribute("satisfactions", satisfactions);
		model.addAttribute("satisfactionsArray",
				arrayJsonString(satisfactions, "Satisfaction"));
		model.addAttribute("difficulties", difficulties);
		model.addAttribute("difficultiesArray",
				arrayJsonString(difficulties, "Difficulty"));
		model.addAttribute("comments", comments);
		return "admin/session";
	}

	String arrayJsonString(Map<?, Long> map, String label) throws IOException {
		Supplier<List<Object[]>> listSupplier = () -> {
			List<Object[]> result = new ArrayList<>();
			result.add(new Object[] { label, "Count" });
			return result;
		};
		return arrayJsonString(map, listSupplier);
	}

	String arrayJsonString(Map<?, Long> map, Supplier<List<Object[]>> listSupplier)
			throws IOException {
		return objectMapper.writeValueAsString(map.entrySet().stream()
				.map(e -> new Object[] { e.getKey(), e.getValue() })
				.collect(toCollection(listSupplier)));
	}

	Map<String, Long> satisfactionMap(Map<Satisfaction, Long> map, Locale locale) {
		Map<Satisfaction, Long> satisfactions = Stream.of(Satisfaction.values())
				.collect(toMap(identity(), e -> 0L, (k, v) -> v, LinkedHashMap::new));
		map.forEach(satisfactions::put);
		return satisfactions.entrySet().stream()
				.collect(toMap(e -> satisfaction(e.getKey(), locale), Map.Entry::getValue,
						(k, v) -> v, LinkedHashMap::new));
	}

	Map<String, Long> difficultyMap(Map<Difficulty, Long> map, Locale locale) {
		Map<Difficulty, Long> difficulties = Stream.of(Difficulty.values())
				.collect(toMap(identity(), e -> 0L, (k, v) -> v, LinkedHashMap::new));
		map.forEach(difficulties::put);
		return difficulties.entrySet().stream()
				.collect(toMap(e -> difficulty(e.getKey(), locale), Map.Entry::getValue,
						(k, v) -> v, LinkedHashMap::new));
	}

	String satisfaction(Satisfaction satisfaction, Locale locale) {
		return messageSource.getMessage(
				"satisfaction." + satisfaction.name().toLowerCase(), null, locale);
	}

	String difficulty(Difficulty difficulty, Locale locale) {
		return messageSource.getMessage("difficulty." + difficulty.name().toLowerCase(),
				null, locale);
	}

	Map<LocalDateTime, Long> countsForSession(
			List<ResponseForSession> responseForSessions) {
		Map<LocalDateTime, Long> countsForSessions = responseForSessions.stream()
				.map(r -> r.getCreatedAt()
						.atOffset(ZoneOffset.ofHours(18 /* 9 + 9 WTF! */))
						.toLocalDateTime().withMinute(0).withSecond(0).withNano(0))
				.collect(groupingBy(identity(), TreeMap::new, counting()));
		return fillZero(countsForSessions);
	}

	Map<LocalDateTime, Long> countsForSeminar(
			List<ResponseForSeminar> responseForSeminar) {
		Map<LocalDateTime, Long> countsForSeminar = responseForSeminar.stream()
				.map(r -> r.getCreatedAt()
						.atOffset(ZoneOffset.ofHours(18 /* 9 + 9 WTF! */))
						.toLocalDateTime().withMinute(0).withSecond(0).withNano(0))
				.collect(groupingBy(identity(), TreeMap::new, counting()));
		return fillZero(countsForSeminar);
	}

	Map<LocalDateTime, Long> fillZero(Map<LocalDateTime, Long> counts) {
		if (!counts.isEmpty()) {
			Iterator<LocalDateTime> iterator = counts.keySet().iterator();
			LocalDateTime earliest = iterator.next();
			LocalDateTime latest = earliest;
			while (iterator.hasNext()) {
				latest = iterator.next();
			}
			for (LocalDateTime dt = earliest; dt.isBefore(latest); dt = dt.plusHours(1)) {
				if (!counts.containsKey(dt)) {
					counts.put(dt, 0L);
				}
			}
		}
		return counts;
	}
}
