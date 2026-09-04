package com.ifsc.contacerta.service;

import com.ifsc.contacerta.dto.studentdashboard.StudentFinancialTipResponse;
import com.ifsc.contacerta.entity.FinancialTip;
import com.ifsc.contacerta.repository.FinancialTipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentFinancialTipService {

	private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");

	private final FinancialTipRepository financialTipRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public StudentFinancialTipResponse currentTip() {
		LocalDate date = LocalDate.now(clock.withZone(SAO_PAULO_ZONE));
		List<FinancialTip> scheduledTips = financialTipRepository
				.findByActiveTrueAndArchivedAtIsNullAndPublicationDateOrderByIdAsc(date);
		if (!scheduledTips.isEmpty()) {
			return toResponse(scheduledTips.getFirst());
		}

		List<FinancialTip> tips = financialTipRepository.findByActiveTrueAndArchivedAtIsNullOrderByIdAsc();
		if (tips.isEmpty()) {
			return null;
		}
		return toResponse(tips.get(Math.floorMod(date.toEpochDay(), tips.size())));
	}

	private StudentFinancialTipResponse toResponse(FinancialTip tip) {
		return new StudentFinancialTipResponse(
				tip.getId(), tip.getTitle(), tip.getContent(), tip.getSourceUrl(), tip.getPublicationDate()
		);
	}
}
