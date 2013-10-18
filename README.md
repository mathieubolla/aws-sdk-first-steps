aws-sdk-first-steps
===================

Introduction to AWS Java SDK

Step 1: Maven glue
===

Lets start with some Maven glue

### Aws Java SDK

Everybody knows some Maven repository. We will search for "AWS SDK" and find it. Example:
[Reposirory](http://mvnrepository.com)
[Search](http://mvnrepository.com/search.html?query=aws+sdk)
[Found](http://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk)
[Best version](http://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk/1.6.1)

### Some cool utilities

Lets add some cool well-known Apache utilities:
[Commons IO](http://commons.apache.org/proper/commons-io/)
[Commons lang](http://commons.apache.org/proper/commons-lang/)

### Check

Check this out: `mvn clean install`

Step 2: Getting dirty
===

`com.amazonaws.auth.AWSCredentials` is the magic interface to avoid the "Hoh my..." AWS Signature problem, best described [there](http://docs.aws.amazon.com/general/latest/gr/signing_aws_api_requests.html). `com.amazonaws.auth.BasicAWSCredentials is the simplest possible implementation, that asks you your credentials from [there](https://console.aws.amazon.com/iam/home?#security_credential).

Once granted the credentials (you will have to create an account, and ask for a new Access Key), you can fill in the com.amazonaws.auth.BasicAWSCredentials(String accessKeyId, String accessKeySecret)`constructor, and be granted access to Amazon Web Services.

Lets check it works with our first call to Amazon Simple Storage Service (S3 in short form):

    AWSCredentials credentials = new BasicAWSCredentials("my-access-key-id", "my-secret-key-VERY-PRIVATE-ISNT-IT");

    AmazonS3 storage = new AmazonS3Client(credentials);

    System.out.println("Amazon S3 buckets: >>>>>>>>>>\n");
    for (Bucket bucket : storage.listBuckets()) {
        System.out.println(bucket);
    }
    System.out.println("\nAmazon S3 buckets: <<<<<<<<<<\n");

One more thing: Nobody want its credentials in open source code, not even in code at all. Lets use `com.amazonaws.auth.PropertiesCredentials` and store our credentials in `~/.ec2/credentials.properties`. The following should do:

    accessKey=my-access-key-id
    secretKey=my-secret-key-MUCH-MORE-PRIVATE-ISNT-IT

Replace your AwsCredentials with `AWSCredentials credentials = new PropertiesCredentials(new File(new File(System.getProperty("user.home"), ".ec2"), "credentials.properties"));`. Much better.

Lets go: `mvn clean install exec:java -Dexec.mainClass="com.myproject.Launcher"`

Basic trouble shooting: If you get message `com.amazonaws.services.s3.model.AmazonS3Exception: Status Code: 403, AWS Service: Amazon S3, AWS Request ID: AD413BCD3F522B20, AWS Error Code: InvalidAccessKeyId, AWS Error Message: The AWS Access Key Id you provided does not exist in our records.` then you input the wrong key id or secret. Double check your identifiers.

If all goes well, you will see nothing but the header lines, because you currently have no Amazon S3 bucket (or you lied and already know how to use S3).

Step 3: Our first upload
===

Lets add some maven magic to deal with dependencies. We don't want our project to be split in multiple unmanageable parts. See pom.xml to know how. We can now run with:

    mvn clean install && java -cp target/aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar com.myproject.Launcher

Thats means that this jar file is the only thing required to launch our beast. Lets make it "self uploadable"...

First, we need a bucket to store our files. Here is one:

    private static void checkCreateBucket(AmazonS3 storage, String bucket) {
        if (!exists(storage, bucket)) {
            storage.createBucket(new CreateBucketRequest(bucket, Region.EU_Ireland));
        }
    }

Next, we need to upload our file to the bucket:

    private static void upload(AmazonS3 storage, InputStream inputStream, String bucketName, String key, String contentType, CannedAccessControlList acl) throws FileNotFoundException {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(contentType);
        storage.putObject(
                new PutObjectRequest(bucketName, key, inputStream, objectMetadata)
                    .withCannedAcl(acl));
    }

Now, our magic command line will upload our jar file to our bucket:

    mvn clean install && java -cp target/aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar com.myproject.Launcher

Wanna make sure all went well? Get our of command line, and check [there](https://console.aws.amazon.com/s3/home?region=eu-west-1#)

Step 4: Lets run it
===

Now, we've got some code in the cloud. Lets run it.

Most EC2 Linux machines can run small (some kB) shell scripts at startup. That's much more than needed. We will get our code local to the machine, and run it. Sounds good? Go!

Huh hoh... For your machine to download code, you need code to be public on S3. But everybody with internet connection can download your public code, you don't want to do it with critical code. How to solve that? Use public code that only download private code, and starts it. I've got the [solution](https://github.com/mathieubolla/aws-sdk-bootstraper), that resolves to:

    curl "http://code.mathieu-bolla.com/maven/snapshot/aws-sdk-bootstraper/aws-sdk-bootstraper/1.0-SNAPSHOT/aws-sdk-bootstraper-1.0-20131018.100941-1-jar-with-dependencies.jar" > bootstraper.jar

See ./shell/startupScript.sh for the pretty details.

Want to test all goes well? Lets have the EC2 machine do some upload. See modified Launcher for details. It will create a file with hostname and date on S3:

    mvn clean install && java -cp target/aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar com.myproject.Launcher launch

Step 5: Security
===

Noticed something wrong with previous step? Right. The machine could not download your private code, could not run it, and therefore could not create the file. Lets add the required "~/.ec2/credentials.properties

Or maybe not? Are you sure you wanna deploy your credentials to a public file? There should be a solution. And there is, deeply hidden in the SDK. An EC2 machine can run as a "Role". A role is a set of policies defined in Amazon Identity Access Management (IAM) that grants permissions on resources accross all of Amazon Web Services.

But how do the Java program know about the credentials? Doesn't matter. If you provide no credentials and the machine has a role, the SDK will do the magic for you (in fact, get fresh credentials every 15 minutes from a private server somewhere on the private Amazon network and update them in signature)

But how do I use my own credentials then? Introducing `com.amazonaws.auth.AWSCredentialsProvider`:

    private AWSCredentialsProvider getAwsCredentials() {
        try {
            return new StaticCredentialsProvider(new PropertiesCredentials(new File(System.getProperty("user.home"), ".ec2/credentials.properties")));
        } catch (IOException e) {
            return new DefaultAWSCredentialsProviderChain();
        }
    }

You can use it everywhere you needed an `AWSCredentials` (in each service client constructor). Only sad thing is that it doesn't implement same interface to be drop-in replacement. Now, your code will attempt credentials from local disk, and default to SDK supplied "Role" credentials.

Now, we have to grant the required policies to our "Role", and run the machine as this one. Example policy for S3 is [there](./securityPolicies/codeDownloader.txt). Assume policy enables the role to trust EC2. Example assume policy [there](./securityPolicies/assumePolicy.txt)

    AmazonIdentityManagement identityManagement = new AmazonIdentityManagementClient(credentials);
    identityManagement.createRole(new CreateRoleRequest().withRoleName("runner").withAssumeRolePolicyDocument(FileUtils.readFileToString(new File("./securityPolicies/assumePolicy.txt"))));
    identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("codeDownloader").withPolicyDocument(FileUtils.readFileToString(new File("./securityPolicies/codeDownloader.txt"))));
    identityManagement.putRolePolicy(new PutRolePolicyRequest().withRoleName("runner").withPolicyName("fileUploader").withPolicyDocument(FileUtils.readFileToString(new File("./securityPolicies/fileUploader.txt"))));
    identityManagement.createInstanceProfile(new CreateInstanceProfileRequest().withInstanceProfileName("runnerProfile"));
    identityManagement.addRoleToInstanceProfile(new AddRoleToInstanceProfileRequest().withInstanceProfileName("runnerProfile").withRoleName("runner"));

    GetInstanceProfileResult runnerProfile = identityManagement.getInstanceProfile(new GetInstanceProfileRequest().withInstanceProfileName("runnerProfile"));
    return runnerProfile.getInstanceProfile().getArn();

You're better wrapping each line in its own try-catch statement to avoid problems when running for the second time (policies will then already exist). We now have a "Role" named "runner". Lets use it:

    machines.runInstances(
            new RunInstancesRequest()
                    .withImageId("ami-c7c0d6b3") // This used to be the official, Ireland running, 32 bit Amazon Machine Image. Or pick, for instance, [Ubuntu](http://cloud-images.ubuntu.com/locator/ec2/)
                    .withInstanceType(InstanceType.T1Micro) // Smallest possible, cheapest. Be warned: Cc28xlarge can set you back 3.75$ per call per machine per hour... [Pricing](http://aws.amazon.com/fr/ec2/#pricing)
                    .withMaxCount(count)
                    .withMinCount(count)
                    .withIamInstanceProfile(new IamInstanceProfileSpecification().withName("runner"))
                    .withUserData(printBase64Binary(FileUtils.readFileToString(new File(pathToScript), "UTF-8").getBytes("UTF-8")))

Ready? Go!

    mvn clean install && java -cp target/aws-sdk-first-steps-1.0-SNAPSHOT-jar-with-dependencies.jar com.myproject.Launcher launch

You should now have a file somewhere in your bucket with time stamp and host name, that contains "I was there". Cool, huh?
Step 5.1: Get ready for the mustache
---

We have written two policy files that deserve some dynamic variables. Lets factor in some Mustache kinda templates for added flexibility.