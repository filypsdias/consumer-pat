package br.com.alelo.consumer.consumerpat.services;

import br.com.alelo.consumer.consumerpat.constants.ErrorCodeEnum;
import br.com.alelo.consumer.consumerpat.constants.ErrorMessages;
import br.com.alelo.consumer.consumerpat.constants.ValidationConstraints;
import br.com.alelo.consumer.consumerpat.entity.Consumer;
import br.com.alelo.consumer.consumerpat.entity.Extract;
import br.com.alelo.consumer.consumerpat.exceptions.OperationException;
import br.com.alelo.consumer.consumerpat.exceptions.PurchaseException;
import br.com.alelo.consumer.consumerpat.helpers.purchase.factories.PurchaseFactory;
import br.com.alelo.consumer.consumerpat.helpers.purchase.strategies.PurchaseStrategy;
import br.com.alelo.consumer.consumerpat.respository.ConsumerRepository;
import br.com.alelo.consumer.consumerpat.respository.ExtractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerServiceImpl implements ConsumerService {

    private final ConsumerRepository repository;
    private final ExtractRepository extractRepository;

    private final PurchaseFactory purchaseFactory;

    @Override
    public List<Consumer> listAllConsumers() {

        log.info("ConsumerServiceImpl.listAllConsumers - Start - No Inputs");
        log.debug("ConsumerServiceImpl.listAllConsumers - Start - No Inputs");

        List<Consumer> consumers = repository.findAll();

        log.debug("ConsumerServiceImpl.listAllConsumers - End - No Inputs. Output {}", consumers);

        return consumers;
    }

    @Override
    @Transactional
    public Consumer createConsumer(final Consumer consumer) {

        log.info("ConsumerServiceImpl.createConsumer - Start");
        log.debug("ConsumerServiceImpl.createConsumer - Start - Input: {}", consumer);

        Consumer created = repository.save(consumer);

        log.debug("ConsumerServiceImpl.createConsumer - Start - Input: {}. Output: {}", consumer, created);

        return created;
    }

    @Override
    @Transactional
    // Não deve ser possível alterar o saldo do cartão
    public Consumer updateConsumer(final Consumer consumer) {

        log.info("ConsumerServiceImpl.updateConsumer - Start");
        log.debug("ConsumerServiceImpl.updateConsumer - Start - Input: {}", consumer);

        Consumer oldRecord = repository.findById(consumer.getId())
                .orElseThrow(() -> {
                    throw new PurchaseException(
                            ErrorCodeEnum.CONSUMER_NOT_FOUND,
                            ErrorMessages.NOT_FOUND,
                            ValidationConstraints.CONSUMER_NOT_FOUND_BY_ID);
                });

        if (!oldRecord.getCard().equals(consumer.getCard())) {
            throw new PurchaseException(
                    ErrorCodeEnum.BALANCE_UPDATE_NOT_ALLOWED,
                    ErrorMessages.UNAUTHORIZED,
                    ValidationConstraints.BALANCE_UPDATE_NOT_ALLOWED);
        }

        Consumer updated = repository.save(consumer);

        log.debug("ConsumerServiceImpl.updateConsumer - Start - Input: {}. Output: {}", consumer, updated);

        return updated;
    }

    @Override
    @Transactional
    public Consumer setCardBalance(final int cardNumber, final double value) {

        log.info("ConsumerServiceImpl.setCardBalance - Start");
        log.debug("ConsumerServiceImpl.setCardBalance - Start - Input - Card Number: {}, Value: {}", cardNumber, value);

        Consumer consumer = repository.findByCardDrugstoreCardNumber(cardNumber).orElse(null);

        if (nonNull(consumer)) {
            consumer.getCard()
                    .setDrugstoreCardBalance(consumer.getCard().getDrugstoreCardBalance() + value);
        } else {
            consumer = repository.findByCardFoodCardNumber(cardNumber).orElse(null); //TODO Change this
            if (nonNull(consumer)) {
                consumer.getCard()
                        .setFoodCardBalance(consumer.getCard().getFoodCardBalance() + value);
            } else {
                consumer = repository.findByCardFuelCardNumber(cardNumber).orElse(null);
                if (isNull(consumer)) {
                    throw new OperationException(
                            ErrorCodeEnum.CONSUMER_NOT_FOUND,
                            ErrorMessages.NOT_FOUND,
                            StringUtils.replace(ValidationConstraints.CONSUMER_NOT_FOUND_BY_ID, "{}", String.valueOf(cardNumber))
                    );
                }
                consumer.getCard()
                        .setFuelCardBalance(consumer.getCard().getFuelCardBalance() + value);
            }
        }
        repository.save(consumer);

        return consumer;
    }

    /* O valores só podem ser debitados dos cartões com os tipos correspondentes ao tipo do estabelecimento da compra.
     *  Exemplo: Se a compra é em um estabelecimeto de Alimentação(food) então o valor só pode ser debitado do cartão e alimentação
     *
     * Tipos de estabelcimentos
     * 1 - Alimentação (food)
     * 2 - Farmácia (DrugStore)
     * 3 - Posto de combustivel (Fuel)
     */
    @Override
    @Transactional
    public Extract buy(final int establishmentType, final String establishmentName, final int cardNumber,
                    final String productDescription, double value) {

        log.info("ConsumerServiceImpl.buy - Start - Input - [{}, {}, {}, {}]",
                establishmentType, establishmentName, productDescription, value);
        log.debug("ConsumerServiceImpl.buy - Start - Input - [{}, {}, {}, {}, {}]",
                establishmentType, establishmentName, cardNumber, productDescription, value);

        PurchaseStrategy strategy = purchaseFactory.findStrategy(establishmentType);
        value = strategy.buy(cardNumber, value);

        log.debug("ConsumerServiceImpl.buy - Strategy found: {}", strategy);
        log.debug("ConsumerServiceImpl.buy - New Value after Strategy: {}", value);

        Extract extract = new Extract(establishmentName, productDescription, new Date(), cardNumber, value);
        extractRepository.save(extract);

        return extract;
    }

}
