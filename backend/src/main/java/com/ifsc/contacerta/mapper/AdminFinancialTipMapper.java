package com.ifsc.contacerta.mapper;

import com.ifsc.contacerta.dto.admin.AdminFinancialTipResponse;
import com.ifsc.contacerta.entity.FinancialTip;

public final class AdminFinancialTipMapper {

	private AdminFinancialTipMapper() {
	}

	public static AdminFinancialTipResponse toResponse(FinancialTip tip) {
		return new AdminFinancialTipResponse(
				tip.getId(), tip.getTitle(), tip.getContent(), tip.getSourceUrl(), tip.getPublicationDate(),
				tip.isActive(), tip.getCreatedAt(), tip.getUpdatedAt(), tip.getVersion(), tip.getArchivedAt()
		);
	}
}
