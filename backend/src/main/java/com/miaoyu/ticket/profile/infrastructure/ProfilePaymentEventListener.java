package com.miaoyu.ticket.profile.infrastructure;

import com.miaoyu.ticket.order.event.PaymentSucceededEvent;
import com.miaoyu.ticket.profile.application.ProfileBehaviorRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 支付事务提交后才记录画像行为；画像失败不能回滚已经支付成功的订单。 */
@Component
public class ProfilePaymentEventListener {
  private final ProfileBehaviorRecorder recorder;

  public ProfilePaymentEventListener(ProfileBehaviorRecorder recorder) {
    this.recorder = recorder;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPaymentSucceeded(PaymentSucceededEvent event) {
    recorder.recordPayment(event);
  }
}
