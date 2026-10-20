package vn.doit.hrm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/** Job 00:05 bị lỡ (app tắt lúc đó) thì chạy bù khi ứng dụng khởi động xong (M1). */
class TerminationSchedulerTest {

    @Test
    void appliesDueTerminationsWhenApplicationIsReady() {
        TerminationService service = mock(TerminationService.class);
        when(service.dueRecordIds()).thenReturn(List.of(7L, 8L));
        when(service.applyScheduled(7L)).thenThrow(new RuntimeException("lỗi một người"));
        when(service.applyScheduled(8L)).thenReturn(true);

        new TerminationScheduler(service).applyDueOnStartup();

        verify(service).applyScheduled(7L);
        verify(service).applyScheduled(8L);
    }

    @Test
    void startupRunNeverFailsStartup() {
        TerminationService service = mock(TerminationService.class);
        when(service.dueRecordIds()).thenThrow(new RuntimeException("CSDL chưa sẵn sàng"));
        assertThatNoException().isThrownBy(() -> new TerminationScheduler(service).applyDueOnStartup());
    }

    @Test
    void startupRunListensToApplicationReady() throws Exception {
        Method m = TerminationScheduler.class.getMethod("applyDueOnStartup");
        EventListener listener = m.getAnnotation(EventListener.class);
        assertThat(listener).isNotNull();
        assertThat(listener.value()).containsExactly(ApplicationReadyEvent.class);
    }
}
