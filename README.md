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
