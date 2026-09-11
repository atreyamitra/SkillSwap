package com.skillswap.app.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.database.ChildEventListener;
import com.skillswap.app.adapters.MessageAdapter;
import com.skillswap.app.databinding.ActivityChatBinding;
import com.skillswap.app.firebase.AuthManager;
import com.skillswap.app.firebase.ChatRepository;
import com.skillswap.app.models.Message;

/**
 * A single 1-1 conversation, backed by messages/{conversationId}/{messageId} with a
 * live ChildEventListener so both sides see new messages instantly while the
 * activity is open.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_OTHER_UID = "extra_other_uid";
    public static final String EXTRA_OTHER_NAME = "extra_other_name";

    private ActivityChatBinding binding;
    private final ChatRepository chatRepository = new ChatRepository();
    private MessageAdapter adapter;
    private ChildEventListener messageListener;
    private String otherUid;
    private String currentUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        otherUid = getIntent().getStringExtra(EXTRA_OTHER_UID);
        String otherName = getIntent().getStringExtra(EXTRA_OTHER_NAME);
        currentUid = AuthManager.getInstance().getCurrentUid();

        binding.toolbarInclude.tvToolbarTitle.setText(otherName == null ? "Chat" : otherName);
        binding.toolbarInclude.btnBack.setOnClickListener(v -> finish());

        adapter = new MessageAdapter(currentUid);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setAdapter(adapter);

        binding.btnSend.setOnClickListener(v -> sendMessage());

        if (otherUid != null && currentUid != null) {
            messageListener = chatRepository.listenForMessages(currentUid, otherUid, message -> {
                adapter.addMessage(message);
                binding.recyclerView.scrollToPosition(adapter.getMessageCount() - 1);
            });
        }
    }

    private void sendMessage() {
        if (currentUid == null || otherUid == null) return;
        String text = binding.etMessage.getText() == null ? "" : binding.etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        Message message = new Message(null, currentUid, otherUid, text);
        chatRepository.sendMessage(currentUid, otherUid, message);
        binding.etMessage.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener != null && otherUid != null && currentUid != null) {
            chatRepository.removeListener(currentUid, otherUid, messageListener);
        }
    }
}
