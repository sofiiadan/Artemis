package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

class EmailSummaryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "emailsummaryservice";

    @Autowired
    private EmailSummaryService weeklyEmailSummaryService;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private CourseRepository courseRepository;

    User userWithActivatedWeeklySummaries;

    User userWithDeactivatedWeeklySummaries;

    private Exercise exerciseReleasedYesterdayAndNotYetDue;

    private static final String USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN = TEST_PREFIX + "student1";

    private static final String USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN = TEST_PREFIX + "student2";

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeEach
    void setUp() {
        database.addUsers(TEST_PREFIX, 2, 0, 0, 0);

        // preparation of the test data where a user deactivated weekly summaries
        this.userWithDeactivatedWeeklySummaries = database.getUserByLogin(USER_WITH_DEACTIVATED_WEEKLY_SUMMARIES_LOGIN);
        NotificationSetting deactivatedWeeklySummarySetting = new NotificationSetting(userWithDeactivatedWeeklySummaries, false, false,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(deactivatedWeeklySummarySetting);

        // preparation of the test data where a user activated weekly summaries
        this.userWithActivatedWeeklySummaries = database.getUserByLogin(USER_WITH_ACTIVATED_WEEKLY_SUMMARIES_LOGIN);

        NotificationSetting activatedWeeklySummarySetting = new NotificationSetting(userWithActivatedWeeklySummaries, false, true,
                NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY);
        notificationSettingRepository.save(activatedWeeklySummarySetting);

        // preparation of test course with exercises for weekly summary testing
        ZonedDateTime now = ZonedDateTime.now();

        Course course = database.createCourse();
        Set<Exercise> allTestExercises = new HashSet<>();

        Exercise exerciseWithoutAReleaseDate = ModelFactory.generateTextExercise(null, null, null, course);
        exerciseWithoutAReleaseDate.setTitle("exerciseWithoutAReleaseDate");
        allTestExercises.add(exerciseWithoutAReleaseDate);

        exerciseReleasedYesterdayAndNotYetDue = ModelFactory.generateTextExercise(now.minusDays(1), null, null, course);
        exerciseReleasedYesterdayAndNotYetDue.setTitle("exerciseReleasedYesterdayAndNotYetDue");
        exerciseReleasedYesterdayAndNotYetDue.setDifficulty(DifficultyLevel.EASY);
        allTestExercises.add(exerciseReleasedYesterdayAndNotYetDue);

        Exercise exerciseReleasedYesterdayButAlreadyClosed = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(5), null, course);
        exerciseReleasedYesterdayButAlreadyClosed.setTitle("exerciseReleasedYesterdayButAlreadyClosed");
        allTestExercises.add(exerciseReleasedYesterdayButAlreadyClosed);

        Exercise exerciseReleasedTomorrow = ModelFactory.generateTextExercise(now.plusDays(1), null, null, course);
        exerciseReleasedTomorrow.setTitle("exerciseReleasedTomorrow");
        allTestExercises.add(exerciseReleasedTomorrow);

        Exercise exerciseReleasedAMonthAgo = ModelFactory.generateTextExercise(now.minusMonths(1), null, null, course);
        exerciseReleasedAMonthAgo.setTitle("exerciseReleasedAMonthAgo");
        allTestExercises.add(exerciseReleasedAMonthAgo);

        course.setExercises(allTestExercises);
        courseRepository.save(course);

        exerciseRepository.saveAll(allTestExercises);
        weeklyEmailSummaryService.setScheduleInterval(Duration.ofDays(7));

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    /**
     * Tests if the method/runnable prepareEmailSummaries correctly selects exercises that are suited for weekly summaries
     */
    @Test
    void testIfPrepareWeeklyEmailSummariesCorrectlySelectsExercisesAndCreatesEmail() {
        var filteredUsers = weeklyEmailSummaryService.findRelevantUsersForSummary();
        assertThat(filteredUsers).contains(userWithActivatedWeeklySummaries);
        assertThat(filteredUsers).doesNotContain(userWithDeactivatedWeeklySummaries);

        when(exerciseRepository.findAllExercisesForSummary(any(), any())).thenReturn(Set.of(exerciseReleasedYesterdayAndNotYetDue));

        weeklyEmailSummaryService.prepareEmailSummariesForUsers(Set.of(userWithActivatedWeeklySummaries));

        ArgumentCaptor<Set<Exercise>> captor = ArgumentCaptor.forClass(Set.class);
        verify(mailService, timeout(5000).times(1)).sendWeeklySummaryEmail(eq(userWithActivatedWeeklySummaries), captor.capture());
        verify(javaMailSender, timeout(5000).times(1)).send(any(MimeMessage.class));

        Set<Exercise> capturedExerciseSet = captor.getValue();
        assertThat(capturedExerciseSet).as("Weekly summary should contain exercises that were released yesterday and are not yet due.")
                .contains(exerciseReleasedYesterdayAndNotYetDue);
        assertThat(capturedExerciseSet.size()).as("Weekly summary should not contain any other of the test exercises.").isEqualTo(1);
    }
}
