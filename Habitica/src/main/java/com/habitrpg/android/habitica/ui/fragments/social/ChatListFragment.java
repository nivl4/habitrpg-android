package com.habitrpg.android.habitica.ui.fragments.social;

import com.habitrpg.android.habitica.APIHelper;
import com.habitrpg.android.habitica.HabiticaApplication;
import com.habitrpg.android.habitica.R;
import com.habitrpg.android.habitica.events.ToggledInnStateEvent;
import com.habitrpg.android.habitica.events.commands.DeleteChatMessageCommand;
import com.habitrpg.android.habitica.events.commands.FlagChatMessageCommand;
import com.habitrpg.android.habitica.events.commands.SendNewGroupMessageCommand;
import com.habitrpg.android.habitica.events.commands.ToggleInnCommand;
import com.habitrpg.android.habitica.events.commands.ToggleLikeMessageCommand;
import com.habitrpg.android.habitica.ui.helpers.UiUtils;
import com.habitrpg.android.habitica.ui.activities.MainActivity;
import com.habitrpg.android.habitica.ui.activities.PrefsActivity;
import com.habitrpg.android.habitica.ui.adapter.social.ChatRecyclerViewAdapter;
import com.habitrpg.android.habitica.ui.fragments.BaseFragment;
import com.habitrpg.android.habitica.ui.helpers.MarkdownParser;
import com.magicmicky.habitrpgwrapper.lib.models.ChatMessage;
import com.magicmicky.habitrpgwrapper.lib.models.HabitRPGUser;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Select;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.functions.Action1;

public class ChatListFragment extends BaseFragment implements SwipeRefreshLayout.OnRefreshListener, Action1<List<ChatMessage>> {

    private Context ctx;
    private String groupId;
    public String seenGroupId;
    private APIHelper apiHelper;
    private HabitRPGUser user;
    private String userId;
    public boolean isTavern;
    private MainActivity activity;

    public void configure(Context ctx, String groupId, APIHelper apiHelper, HabitRPGUser user, MainActivity activity, boolean isTavern) {

        this.ctx = ctx;
        this.groupId = groupId;
        this.apiHelper = apiHelper;
        this.user = user;
        if (this.user != null) {
            this.userId = this.user.getId();
        }
        this.isTavern = isTavern;
        this.activity = activity;
    }

    private View view;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState != null) {
          if (savedInstanceState.containsKey("groupId")) {
            this.groupId = savedInstanceState.getString("groupId");
          }

          if (savedInstanceState.containsKey("isTavern")) {
            this.isTavern = savedInstanceState.getBoolean("isTavern");
          }

          if (savedInstanceState.containsKey("userId")) {
            this.userId = savedInstanceState.getString("userId");
            if (this.userId != null) {
              this.user = new Select().from(HabitRPGUser.class).where(Condition.column("id").eq(userId)).querySingle();
            }
          }

        }

        if (view == null)
            view = inflater.inflate(R.layout.fragment_chatlist, container, false);

        if (apiHelper == null) {
            apiHelper = new APIHelper(PrefsActivity.fromContext(getContext()));
        }

        return view;
    }

    @BindView(R.id.chat_list)
    RecyclerView mRecyclerView;

    @BindView(R.id.chat_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;

    LinearLayoutManager layoutManager;

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ButterKnife.bind(this, view);

        swipeRefreshLayout.setOnRefreshListener(this);


        layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();

        if (layoutManager == null) {
            layoutManager = new LinearLayoutManager(ctx);

            mRecyclerView.setLayoutManager(layoutManager);
        }

        ChatRecyclerViewAdapter tavernAdapter = new ChatRecyclerViewAdapter(new ArrayList<ChatMessage>(), userId, groupId, isTavern);

        mRecyclerView.setAdapter(tavernAdapter);

        onRefresh();
    }

    @Override
    public void onRefresh() {
        swipeRefreshLayout.setRefreshing(true);

        apiHelper.apiService.listGroupChat(groupId).compose(apiHelper.configureApiCallObserver())
                .subscribe(this, throwable -> {});
    }

    private List<ChatMessage> currentChatMessages;

    public void setNavigatedToFragment(String groupId){
        seenGroupId = groupId;
        navigatedOnceToFragment = true;

        markMessagesAsSeen();
    }

    private boolean navigatedOnceToFragment = false;
    private boolean gotNewMessages = false;

    private void markMessagesAsSeen(){
        if(!isTavern && seenGroupId != null && !seenGroupId.isEmpty()
                && gotNewMessages && navigatedOnceToFragment) {

            gotNewMessages = false;

            apiHelper.apiService.seenMessages(seenGroupId)
                    .compose(apiHelper.configureApiCallObserver())
                    .subscribe(s -> {}, throwable -> {});
        }
    }

    @Subscribe
    public void onEvent(final FlagChatMessageCommand cmd) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.chat_flag_confirmation)
                .setPositiveButton(R.string.flag_confirm, (dialog, id) -> {
                    apiHelper.apiService.flagMessage(cmd.groupId, cmd.chatMessage.id)
                            .compose(apiHelper.configureApiCallObserver())
                            .subscribe(aVoid -> {
                        UiUtils.showSnackbar(activity, activity.getFloatingMenuWrapper(), "Flagged message by " + cmd.chatMessage.user, UiUtils.SnackbarDisplayType.NORMAL);
                    }, throwable -> {});
                })
                .setNegativeButton(R.string.action_cancel, (dialog, id) -> {
                });
        builder.show();
    }

    @Subscribe
    public void onEvent(final ToggleLikeMessageCommand cmd) {
        apiHelper.apiService.likeMessage(cmd.groupId, cmd.chatMessage.id).compose(apiHelper.configureApiCallObserver())
                .subscribe(voids -> {}, throwable -> {});
    }

    @Subscribe
    public void onEvent(final DeleteChatMessageCommand cmd) {
        apiHelper.apiService.deleteMessage(cmd.groupId, cmd.chatMessage.id)
                .compose(apiHelper.configureApiCallObserver())
                .subscribe(aVoid -> {
            if (currentChatMessages != null) {
                currentChatMessages.remove(cmd.chatMessage);

                ChatListFragment.this.call(currentChatMessages);
            }
        }, throwable -> {});
    }

    @Subscribe
    public void onEvent(SendNewGroupMessageCommand cmd) {
        HashMap<String, String> messageObject = new HashMap<>();
        messageObject.put("message", cmd.Message);
        apiHelper.apiService.postGroupChat(cmd.TargetGroupId, messageObject)
                .compose(apiHelper.configureApiCallObserver())
                .subscribe(postChatMessageResult -> {
            if (currentChatMessages != null) {
                currentChatMessages.add(0, postChatMessageResult.message);

                ChatListFragment.this.call(currentChatMessages);
            }
        }, throwable -> {});

        UiUtils.dismissKeyboard(HabiticaApplication.currentActivity);
    }

    // If the ChatList is Tavern, we're able to toggle the sleep-mode
    @Subscribe
    public void onEvent(ToggleInnCommand event) {
        apiHelper.apiService.sleep().compose(apiHelper.configureApiCallObserver())
                .subscribe(aVoid -> {
                    ToggledInnStateEvent innState = new ToggledInnStateEvent();
                    innState.Inn = !user.getPreferences().getSleep();

                    user.getPreferences().setSleep(innState.Inn);

                    EventBus.getDefault().post(innState);
                }, throwable -> {});
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("userId", this.userId);
        outState.putString("groupId", this.groupId);
        outState.putBoolean("isTavern", this.isTavern);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void call(List<ChatMessage> chatMessages) {
        currentChatMessages = chatMessages;

        //Load unparsed messages first
        ChatRecyclerViewAdapter tavernAdapter = new ChatRecyclerViewAdapter(chatMessages, userId, groupId, isTavern);

        if(mRecyclerView != null) {
            mRecyclerView.setAdapter(tavernAdapter);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }

        //Parse chatMessages in AsyncTask
        ParseMessages parseMessages = new ParseMessages(chatMessages);
        parseMessages.execute();
        gotNewMessages = true;

        markMessagesAsSeen();
    }

    private class ParseMessages extends AsyncTask<Void, Void, Void> {
        private List<ChatMessage> chatMessages;

        public ParseMessages(List<ChatMessage> chatMessages) {
            this.chatMessages = chatMessages;
        }

        @Override
        protected Void doInBackground(Void... params) {

            for (int i = 0; i < chatMessages.size() ; i++) {
                chatMessages.get(i).parsedText = MarkdownParser.parseMarkdown(chatMessages.get(i).text);
            }

            return null;
        }

        protected void onPostExecute(Void result) {
            ChatRecyclerViewAdapter tavernAdapter = new ChatRecyclerViewAdapter(chatMessages, userId, groupId, isTavern);

            if(mRecyclerView != null) {
                mRecyclerView.setAdapter(tavernAdapter);
            }

            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    }

}
