package com.chris64233.commodityoperations.service;

import com.chris64233.commodityoperations.domain.MarketPrice;
import com.chris64233.commodityoperations.dto.MarketPriceView;
import com.chris64233.commodityoperations.dto.PublishMarketPriceRequest;
import com.chris64233.commodityoperations.repo.MarketPriceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MarketPriceService {

    private final MarketPriceRepository marketPriceRepository;

    public MarketPriceService(MarketPriceRepository marketPriceRepository) {
        this.marketPriceRepository = marketPriceRepository;
    }

    @Transactional
    public MarketPriceView publish(PublishMarketPriceRequest request) {
        if (!request.validFrom().isBefore(request.validTo())) {
            throw new BusinessRuleException("市场价格有效区间必须满足 validFrom < validTo");
        }
        if (marketPriceRepository.existsBySourceAndPriceVersion(
                request.source(), request.priceVersion())) {
            throw new ConflictException(
                    "市场价格版本已存在: " + request.source() + "/" + request.priceVersion());
        }
        MarketPrice saved = marketPriceRepository.save(new MarketPrice(
                request.source(), request.priceVersion(), request.commodity(),
                request.currency(), request.price(), request.validFrom(), request.validTo()));
        return MarketPriceView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MarketPriceView> list() {
        return marketPriceRepository.findAll().stream().map(MarketPriceView::from).toList();
    }

    @Transactional(readOnly = true)
    public MarketPrice requireExisting(String source, String version) {
        return marketPriceRepository.findBySourceAndPriceVersion(source, version)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.BAD_REQUEST,
                        "市场价格版本不存在: " + source + "/" + version));
    }
}
