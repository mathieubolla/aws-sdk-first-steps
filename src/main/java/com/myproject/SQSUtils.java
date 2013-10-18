package com.myproject;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.*;

public class SQSUtils {
    public static void checkCreateQueues(AmazonSQS queue, String... queueNames) {
        for (String queueName : queueNames) {
            queue.createQueue(new CreateQueueRequest().withQueueName(queueName));
        }
    }

    private static String getQueueUrl(AmazonSQS queue, String queueName) {
        return queue.getQueueUrl(new GetQueueUrlRequest().withQueueName(queueName)).getQueueUrl();
    }

    public static String getQueueArn(AmazonSQS queue, String queueName) {
        return queue.getQueueAttributes(new GetQueueAttributesRequest().withAttributeNames("QueueArn").withQueueUrl(getQueueUrl(queue, queueName))).getAttributes().get("QueueArn");
    }

    public static void sendMessageTo(AmazonSQS queue, String queueName, String message) {
        queue.sendMessage(new SendMessageRequest().withQueueUrl(getQueueUrl(queue, queueName)).withMessageBody(message));
    }

    public static String[] readMessageFrom(AmazonSQS queue, String queueName) {
        ReceiveMessageResult receiveMessageResult = queue.receiveMessage(
                new ReceiveMessageRequest()
                        .withQueueUrl(getQueueUrl(queue, queueName))
                        .withMaxNumberOfMessages(1) // Process one at a time
                        .withWaitTimeSeconds(20) // Use long polling client
        );
        if (receiveMessageResult.getMessages().isEmpty()) {
            return readMessageFrom(queue, queueName);
        }

        Message message = receiveMessageResult.getMessages().get(0);
        return new String[]{message.getBody(), message.getReceiptHandle()};
    }

    public static void acknowledge(AmazonSQS queue, String queueName, String receipt) {
        queue.deleteMessage(new DeleteMessageRequest().withQueueUrl(getQueueUrl(queue, queueName)).withReceiptHandle(receipt));
    }

    public static void renewTimeout(AmazonSQS queue, String queueName, String receipt, int timeout) {
        queue.changeMessageVisibility(new ChangeMessageVisibilityRequest().withQueueUrl(getQueueUrl(queue, queueName)).withReceiptHandle(receipt).withVisibilityTimeout(timeout)); // Say I need 20 more seconds to process
    }
}
