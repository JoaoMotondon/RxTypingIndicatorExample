package com.motondon.rxtypingindicatorexample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.jakewharton.rxbinding.widget.RxCompoundButton;
import com.jakewharton.rxbinding.widget.RxTextView;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.CompositeSubscription;

/**
 * This class is intended to demonstrate how to add a typing indicator feature by using reactive programming.
 *
 * We will show some different approaches using different operators combinations. The first two, window() and buffer() will not fit our
 * requirements. The third option uses a combination of publish-ambWith-timer operators that will work exactly as we expect.
 *
 * You can find information about it at http://www.androidahead.com
 *
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @BindView(R.id.etName) EditText etUserName;
    @BindView(R.id.et_text) EditText etText;
    @BindView(R.id.container_typing_indicator) LinearLayout containerTypingIndicator;
    @BindView(R.id.tv_timer) TextView tvTimer;
    @BindView(R.id.tv_typing_indicator) TextView tvTypingIndicator;
    @BindView(R.id.rb_operator_window) RadioButton rbOperatorWindow;
    @BindView(R.id.rb_operator_buffer) RadioButton rbOperatorBuffer;
    @BindView(R.id.rb_operator_publish_amb_timer) RadioButton rbOperatorPublishAmbTimer;

    // Used to avoid memory leak. It will hold all subscribers and will unsubscribe all of them in onDestroy() method.
    private CompositeSubscription compositeSubscription;

    // Used for the timer TextView
    private Subscription timerSubscription;

    // Use to unsubscribe when user change RadioButton option
    private Subscription subscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        compositeSubscription = new CompositeSubscription();

        // This will setup an observable to enable/disable etText EditText when etUserName EditText contains more than 3 characters
        setupUserNameFields();

        // Setup a listener for all three radio-buttons used on this app
        setupRadioButtons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Warning! After call unsubscribe, this object will be unusable. This is not a problem here, since it is being
        // called on onDestroy.
        compositeSubscription.unsubscribe();
    }

    private void setupUserNameFields() {

        // Only enable EditText etText when userName length is greater than 3 characters. This is just to demonstrate a
        // simple RxBinding usage
        Subscription subscription1 = RxTextView.textChanges(etUserName)
                .doOnNext(s -> Log.d(TAG, "User Name: " + s))
                .map(s -> (s.length() > 3))
                .subscribe(etText::setEnabled);

        // Add this subscription to the composite in order to unsubscribe it when this activity is destroyed.
        compositeSubscription.add(subscription1);
    }

    private void setupRadioButtons() {

        RxCompoundButton.checkedChanges(rbOperatorWindow)
            .subscribe(
                checked -> {
                    if (checked) {
                        Log.d(TAG, "rbOperatorWindow checked");
                        if (subscription != null) {
                            subscription.unsubscribe();
                            compositeSubscription.remove(subscription);
                        }
                        // When user chooses window() operator setup an observable in order to use it.
                        setupTypingIndicatorUsingWindowOperator();
                    }
                }
            );

        RxCompoundButton.checkedChanges(rbOperatorBuffer)
            .subscribe(
                checked -> {
                    if (checked) {
                        Log.d(TAG, "rbOperatorBuffer checked");
                        if (subscription != null) {
                            subscription.unsubscribe();
                            compositeSubscription.remove(subscription);
                        }
                        // When user chooses buffer() operator setup an observable in order to use it.
                        setupTypingIndicatorUsingBufferOperator();
                    }
                }
            );

        RxCompoundButton.checkedChanges(rbOperatorPublishAmbTimer)
            .subscribe(
                checked -> {
                    if (checked) {
                        Log.d(TAG, "rbOperatorPublishAmbTimer checked");
                        if (subscription != null) {
                            subscription.unsubscribe();
                            compositeSubscription.remove(subscription);
                        }
                        // When user chooses ambWith() operator setup an observable in order to use it.
                        setupTypingIndicatorUsingAmbWithOperator();
                    }
                }
            );
    }

    /**
     * This example uses window() operator. We are using a variant which accepts a timespan and a count parameters. This means that it will
     * emit an observable either when "count" is reached or when timespan expires.
     *
     * Basically, if it emits an empty observable, it means user did not type anything within 5 seconds. So we will hide typing indicator
     * message. When it emits any value, it means user typed something. So, we will show typing indicator message.
     *
     * The problem is that when user types something, window() operator makes current window to be emitted, but it will only start another
     * window either if user types something else (which makes it to start and close a new window and emit it immediately) or after the latest
     * timespan expires.
     *
     * Let's see an example:
     *
     * At time 0:  a new window is started (which is expected to be opened for 5 seconds)
     * At time 1:  user types letters 'aaa'. Current window is closed and those items are emitted. This will make typing indicator message to
     *             appear.
     * At time 5:  another window is opened.
     * At time 10: window is closed: Only now typing indicator message is gone, since now window operator emitted an empty observable.
     *             Here typing indicator message was visible for 9 seconds.
     * At time 14: user types letter 'b'. Current window is closed and that item is emitted. This makes typing indicator message to appear.
     * At time 15: another window is opened.
     * At time 20: window is closed: Only now typing indicator message is gone. Now typing indicator message was visible for 7 seconds.
     *
     * We can see that behavior mentioned above when user typed some characters at time 1 making current window to be emitted immediately, but
     * only at time 5 it started a new window and emitted it only after 5 seconds (i.e.: at time 10).
     *
     * Since we want to make typing indicator message to be gone EXACTLY after 5 seconds user stop typing, this does not fit our needs.
     *
     */
    private void setupTypingIndicatorUsingWindowOperator() {
        Log.d(TAG, "setupTypingIndicatorUsingWindowOperator()");

        subscription = RxTextView
            .textChanges(etText)
            .filter(t -> t.length() > 2)
            .window(5, TimeUnit.SECONDS, 1)
            .flatMap(t -> t.toList())

            // This line will add a log entry whenever window operation emits, even when there observable is empty.
            // This is helpful during the development phase.
            //.compose(showDebugMessages("window"))

            // This operator will prevent emissions downstream until window changes its status (i.e. if it is emitting an empty observable every second, these
            // emissions will not be propagated down until user type something and window() emits that value.
            //.distinctUntilChanged()

            // Now, just map our List<Boolean> to a boolean object based on the list length.
            .map(data -> data.size() == 0 ? false : true)

            // This will print false in case of an empty observable (i.e. nothing typed within 5 seconds). Otherwise it will print true.
            //.compose(showDebugMessages("map (after window)"))

            // Observe on the Main Thread, since we will touch in the GUI objects.
            .observeOn(AndroidSchedulers.mainThread())

            .subscribe(
                hasData -> {
                    Log.d(TAG, "onNext(): " + hasData);
                    // This is where the magic happens. If observable contains data, we just print our typing indicator. Otherwise we hide it.
                    if (hasData) {
                        tvTypingIndicator.setText(etUserName.getText().toString() + " is typing");

                        // Start a timer in order to show to the user for how long he/she is typing
                        startTimer();
                    } else {
                        tvTypingIndicator.setText("");
                        stopTimer();
                    }
                },
                error -> Log.e(TAG, "onError(): " + error.getMessage()),
                () -> Log.d(TAG, "onCompleted")
            );

        compositeSubscription.add(subscription);
    }

    /**
     * This example uses buffer() operator. We are using a variant which accepts a timespan and a count parameters. This means that it will
     * emit an observable either when "count" is reached or when timespan expires.
     *
     * For the variant we are using, it will always emit something when it's timespan elapses, regardless whether it is fewer than count. This means
     * that our buffer() operator will emit each 5 seconds, no matter what happens. Within this period, if user types something, that item will be
     * emitted (since count = 1), and when it elapses 5 seconds since its last bundle emission, it will emit an empty list, making our typing indicator
     * message to be gone.
     *
     * Using buffer() operator differs a little than using window(), but still does not fit what we are looking for since if user types something
     * right before timespan elapses (e.g.: 500ms before), typing indicator message will be visible for a very short period of time.
     *
     * Let's see an example:
     *
     * At time 0:  a new timespan is started (which is expected to be opened for 5 seconds)
     * At time 1:  user types letters 'aaa'. These items are emitted. This will make typing indicator message to appear.
     * At time 5:  the first timespan elapses, so an empty list is emitted making typing indicator message to be gone. Notice that the indicator
     *             message was visible for only 4 seconds.
     * At time 9:  user types letter 'b'. This item is emitted. This will make typing indicator message to appear.
     * At time 10: the latest timespan elapsed, so an empty list is emitted making indicator message to be gone. It was visible for only 1 second.
     *
     * We clearly see that by using buffer operator, the effect is the opposite than when using window() operator. Depends on the period of its timespan
     * user types something, typing indicator message will be visible from some milliseconds to up to 5 seconds.
     *
     */
    private void setupTypingIndicatorUsingBufferOperator() {
        Log.d(TAG, "setupTypingIndicatorUsingBufferOperator()");

        subscription = RxTextView
            .textChanges(etText)
            .filter(t -> t.length() > 2)
            .buffer(5, TimeUnit.SECONDS, 1)

            // This line will add a log entry whenever buffer operation emits (i.e.: every 5 seconds), even when there observable is empty.
            // This is helpful during the development phase.
            //.compose(showDebugMessages("buffer"))

            // This operator will prevent emissions downstream until buffer changes its status (i.e. if it is emitting an empty observable every second, these
            // emissions will not be propagated down until user type something and buffer() emits that value.
            //.distinctUntilChanged()

            // Now, just map our List<Boolean> to a boolean based on the list length.
            .map(data -> data.size() == 0 ? false : true)

            // This will print false in case of an empty observable (i.e. nothing typed within 5 seconds). Otherwise it will print true.
            //.compose(showDebugMessages("map (after buffer)"))

            // Observe on the Main Thread, since we will touch in the GUI objects.
            .observeOn(AndroidSchedulers.mainThread())

            .subscribe(
                hasData -> {
                    Log.d(TAG, "onNext(): " + hasData);
                    // This is where the magic happens. If observable contains data, we just print our typing indicator. Otherwise we hide it.
                    if (hasData) {
                        tvTypingIndicator.setText(etUserName.getText().toString() + " is typing");

                        // Start a timer in order to show to the user for how long he/she is typing
                        startTimer();
                    } else {
                        tvTypingIndicator.setText("");
                        stopTimer();
                    }
                },
                error -> Log.e(TAG, "onError(): " + error.getMessage()),
                () -> Log.d(TAG, "onCompleted")
            );

        compositeSubscription.add(subscription);
    }

    /**
     * This example uses a combination of publish-ambWith-timer operators. It is based in an answer from David Karnok on an SO question (see link below):
     *   - http://stackoverflow.com/questions/35873244/reactivex-emit-null-or-sentinel-value-after-timeout
     *
     * According to the docs, amb() operator can have multiple source observables, but will emit all items from ONLY the first of these Observables
     * to emit an item or notification. All the others will be discarded.
     *
     * So, if user types something, it will emit that value and ignore its timer (from the second observable). On the other hand, if the "5 seconds timer"
     * expires prior user type something, it will emit a null value. Later we will check for the emitted value, and if it is null, we hide the typing
     * indicator message. When it contains any value, we show the typing indicator message.
     *
     *  This will give us a null emitted item exactly after five seconds of idleness. So, finally, this is what we were looking for!
     *
     */
    private void setupTypingIndicatorUsingAmbWithOperator() {
        Log.d(TAG, "setupTypingIndicatorUsingAmbWithOperator()");

        PublishSubject<Long> publishSubject = PublishSubject.create();

        subscription = publishSubject.
            publish(selector -> selector
                .take(1)
                .ambWith(Observable
                    .timer(5, TimeUnit.SECONDS)
                    .map(v -> (Long)null))
                .repeat()
                .takeUntil(selector.ignoreElements())
            )

            // This operator will prevent emissions downstream until publish changes its status (i.e. if it is emitting an empty observable every second, these
            // emissions will not be propagated down until user type something and publish() emits that value.
            //.distinctUntilChanged()

            // Observe on the Main Thread, since we will touch in the GUI objects.
            .observeOn(AndroidSchedulers.mainThread())

            .subscribe(
                hasData -> {
                    Log.d(TAG, "onNext(): " + hasData);
                    if (hasData != null) {
                        // This is where the magic happens. If observable contains data, we just print our typing indicator. Otherwise we hide it.
                        tvTypingIndicator.setText(etUserName.getText().toString() + " is typing");

                        // Start a timer in order to show to the user for how long he/she is typing
                        startTimer();
                    } else {
                        tvTypingIndicator.setText("");
                        stopTimer();
                    }
                },
                error -> Log.e(TAG, "onError(): " + error.getMessage()),
                () -> Log.d(TAG, "onCompleted")
            );

        RxTextView
            .textChanges(etText)
            .filter(t -> t.length() > 2)
            .timeInterval()
            .map(t -> t.getIntervalInMilliseconds())
            .subscribe(publishSubject::onNext);

        compositeSubscription.add(subscription);
    }

    private void startTimer() {
        Log.d(TAG, "startTimer()");

        stopTimer();

        // When starting a timer, it means user is typing something, so make that control visible
        containerTypingIndicator.setVisibility(View.VISIBLE);

        // Create a timer which will update a TextView every second in order to show for how long user is typing.
        timerSubscription = Observable.interval(0, 1, TimeUnit.SECONDS)
            .map((tick) ->  tick+1)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                (tick) -> tvTimer.setText(String.format("%02d:%02d", (tick % 3600) / 60, tick % 60)),
                (throwable) -> Log.e(TAG, throwable.getMessage(), throwable));
    }

    private void stopTimer() {
        Log.d(TAG, "stopTimer()");

        // Stop a timer if available
        if (timerSubscription != null) {
            timerSubscription.unsubscribe();
        }

        // When stopping a timer, it means user stopped typing. So, hide that control.
        containerTypingIndicator.setVisibility(View.INVISIBLE);
    }

    /**
     * We are just using Transformer to show debug messages. It is well explained on this article:
     * http://blog.danlew.net/2015/03/02/dont-break-the-chain/
     *
     * @param operatorName
     * @param <T>
     * @return
     *
     */
    private <T> Observable.Transformer<T, T> showDebugMessages(final String operatorName) {

        return (observable) -> observable
            .doOnSubscribe(() -> Log.v(TAG, operatorName + ".doOnSubscribe"))
            .doOnUnsubscribe(() -> Log.v(TAG, operatorName + ".doOnUnsubscribe"))
            .doOnNext((doOnNext) -> Log.v(TAG, operatorName + ".doOnNext. Data: " + doOnNext))
            .doOnCompleted(() -> Log.v(TAG, operatorName + ".doOnCompleted"))
            .doOnTerminate(() -> Log.v(TAG, operatorName + ".doOnTerminate"))
            .doOnError((throwable) -> Log.v(TAG, operatorName + ".doOnError: " + throwable.getMessage()));
    }
}
