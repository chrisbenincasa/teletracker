import { PubSub } from '@google-cloud/pubsub';

const pubsub = new PubSub({
  projectId: 'teletracker',
});

const getTopic = async topicName => {
  try {
    return await pubsub.createTopic(topicName);
  } catch (e) {
    if (e.code === 6) {
      return await pubsub.topic(topicName);
    } else {
      throw e;
    }
  }
};

const publishTaskMessage = async (clazz, args, jobTags) => {
  let payload = {
    clazz,
    args,
    jobTags,
  };
  let topic = await getTopic('teletracker-task-queue');
  return topic.publisher.publish(Buffer.from(JSON.stringify(payload)));
};

export { publishTaskMessage };
