RELEASE_BUILD_NUMBER = Jenkins.instance.getItem( 'UtaPassAndroidDev' ).lastSuccessfulBuild.number
// HighTier_BUILD_NUMBER = Jenkins.instance.getItem( 'HighTierAndroidDevEspresso(RC)' ).BUILD_NUMBER
HighTier_BUILD_NUMBER = BUILD_NUMBER


echo "Current UtaPassAndroidDev build number: ${RELEASE_BUILD_NUMBER}"
echo "Current HighTierAndroidDevEspresso(RC) build number: ${HighTier_BUILD_NUMBER}"


currentBuild.description = "UtaPassAndroidDev #${RELEASE_BUILD_NUMBER}"

void create_apk() {
    dir( "src/utapass" ) {
        git url: 'git@github.com:KKBOX/utapass.git',
            branch: 'utapass_dev',
            credentialsId: '479f3fbf-53d8-4285-be72-6c4e7566313b'
    }

    dir( "src/espresso" ) {
        git url: 'git@github.com:KKStream/utapass_automation.git',
            branch: 'master',
            credentialsId: '479f3fbf-53d8-4285-be72-6c4e7566313b'
    }
    
    dir( "output" ) {
        echo "Clean"
        sh "rm -rf ./*"

        echo "Copy Android"
        sh "cp -R ../src/utapass/* ."

        echo "Clean existing espresso test"
        sh "rm -rf app/src/androidTest/*"

        echo "Copy Espresso"
        sh "mkdir -p app/src/androidTest/java/com/kddi/android/UtaPass/sqa_espresso"
        sh "cp -R ../src/espresso/* app/src/androidTest/java/com/kddi/android/UtaPass/sqa_espresso"
    }

    dir( "output" ) {
        sh "./gradlew :app:assembleNormalSqaDebug \
                      :app:assembleNormalSqaDebugAndroidTest"
    }
}

void screen_on() {
    sh '''
        export PATH=$PATH:/Users/utapass-sqa/Desktop/AndroidSDK/platform-tools/

        # ========================================
        # screen on
        # ========================================
        for device_id in `adb devices | grep 'device\$' | awk '{print \$1}'`; do
            android_version=`adb -s \$device_id shell getprop ro.build.version.release`

            case "\$android_version" in
                4.4.4* )
                    adb -s \$device_id shell dumpsys input_method | \
                        grep -q mScreenOn=false && \
                        adb -s \$device_id shell input keyevent 26
                    ;;

                * )
                    adb -s \$device_id shell dumpsys power | \
                        grep -i  'display power' | \
                        grep -iq 'off' && \
                        adb -s \$device_id shell input keyevent 26 && \
                        adb -s \$device_id shell input keyevent 82
                    ;;
            esac
        done
    sh '''
}

void screen_off() {
    sh '''
        export PATH=$PATH:/Users/utapass-sqa/Desktop/AndroidSDK/platform-tools/

        # ========================================
        # screen off
        # ========================================
        for device_id in `adb devices | grep 'device\$' | awk '{print \$1}'`; do
            adb -s \$device_id shell dumpsys power | \
                grep -i  'display power' | \
                grep -iq 'on' && \
                adb -s \$device_id shell input keyevent 26
        done
    '''
}

void uninstall_utapass() {
    sh '''
        export PATH=$PATH:/Users/utapass-sqa/Desktop/AndroidSDK/platform-tools/

        # ========================================
        # uninstall utapass
        # ========================================
        for device_id in `adb devices | grep 'device\$' | awk '{print \$1}'`; do

            adb -s "\${device_id}" shell pm list package \
                | grep -i 'com.kddi.android.UtaPass.test.home' &> /dev/null \
                && adb -s \${device_id} shell pm uninstall com.kddi.android.UtaPass.test.home \
                || echo "Ignore: failed to uninstall 'com.kddi.android.UtaPass.test.home'"

            adb -s "\${device_id}" shell pm list package \
                | grep -i 'com.kddi.android.UtaPass' &> /dev/null \
                && adb -s "\${device_id}" uninstall com.kddi.android.UtaPass \
                || echo "Ignore: failed to uninstall 'com.kddi.android.UtaPass'"
        done
    '''
}

void install_utapass_and_grant_permissions( String package_path ) {
    sh """
        export PATH=$PATH:/Users/utapass-sqa/Desktop/AndroidSDK/platform-tools/

        # ========================================
        # install utapass & grant permission
        # ========================================
        for device_id in `adb devices | grep 'device\$' | awk '{print \$1}'`; do
            adb -s \${device_id} install -r ${package_path}
            adb -s \${device_id} shell pm grant com.kddi.android.UtaPass android.permission.READ_EXTERNAL_STORAGE
            adb -s \${device_id} shell pm grant com.kddi.android.UtaPass android.permission.READ_PHONE_STATE
            adb -s \${device_id} shell pm grant com.kddi.android.UtaPass android.permission.GET_ACCOUNTS
        done
    """
}

void run_spoon( String classname, String output ) {
    sh """
        export PATH=$PATH:/Users/utapass-sqa/Desktop/AndroidSDK/platform-tools/

        rm -rf ${output}
        mkdir -p ${output}

        # ========================================
        # run spoon
        # ========================================
        java -jar         ~/spoon-runner-1.7.1-jar-with-dependencies.jar \
             --sdk        ~/Desktop/AndroidSDK/ \
             --apk        ./output/app/build/outputs/apk/normal/sqaDebug/app-normal-sqaDebug.apk \
             --test-apk   ./output/app/build/outputs/apk/androidTest/normal/sqaDebug/app-normal-sqaDebug-androidTest.apk \
             --title      "${classname} - Dev Build #${RELEASE_BUILD_NUMBER}"  \
             --class-name com.kddi.android.UtaPass.sqa_espresso.test.highTierPlan.${classname} \
             --output     ${output} \
             --no-animations \
             --grant-all
    """
}

void send_message( String report_name, String fail_count ) {
    url    = " <https://utapass-jenkins.kkinternal.com/view/SQA/job/HighTierAndroidDevEspresso(RC)/${HighTier_BUILD_NUMBER}/${report_name}_20Report/|${report_name} Report Click Me>"
    title  = "【 UtaPass Automation - ( ${report_name} ) 】\n\n"
    result = " Test Result                                 TestBuild \n Failed: ${failcount} 條                               HighTierAndroid Dev #*${RELEASE_BUILD_NUMBER}* \n\n\n"
    report = " Test Report \n ${url}"
    
    slackSend channel: '#up_sqa_auto',
              tokenCredentialId: '7da62e8e-efca-48eb-b162-eb685da6559d',
              color: 'danger',
              message: "${title}${result}${report}"
}

void send_message_when_job_fail() {
    title  = "【 HighTier Automation 】\n\n"
    result = " * JOB Fail 趕快來看！！ *\n\n"
    report = " https://utapass-jenkins.kkinternal.com/view/SQA/job/HighTierAndroidDevEspresso(RC)/${HighTier_BUILD_NUMBER}"

    slackSend channel: '#up_sqa_auto',
              tokenCredentialId: '7da62e8e-efca-48eb-b162-eb685da6559d',
              color: 'danger',
              message: "${title}${result}${report}"
}

void publish_and_send_message( String report_path, String report_name ) {
    junit "$report_path/junit-reports/*.xml"
    
    publishHTML target: [
        allowMissing: false,
        alwaysLinkToLastBuild: false,
        keepAll: true,
        reportDir: "${report_path}",
        reportFiles: "index.html",
        reportName: "${report_name} Report"
    ]

    failcount = sh ( 
        script: """ cat "${report_path}"/index.html | grep 'class=\"test fail\"' | wc -l """, 
        returnStdout: true 
    ).trim()

    if( "${failcount}" != '0' ){
        send_message( "${report_name}", "${failcount}" )
    }
    else{
        echo "${report_name} All Pass"
    }
}

try {
    node( 'SQA-android3' ) {
        stage( 'Build & deploy' ) {

            // build
            create_apk()
            
            // deploy
            screen_on()
            uninstall_utapass()
            install_utapass_and_grant_permissions( "./output/app/build/outputs/apk/normal/sqaDebug/app-normal-sqaDebug.apk" )
        }

        stage( 'HighTierRatCriticalTest' ) {
            run_spoon( "HighTierRatCriticalTest", "report/rat-critical-report" )
        }

        screen_off()

        stage( 'Report' ) {
            publish_and_send_message( "report/rat-critical-report","HighTierAndroidDevEspresso(RC)" )
        }
    }
}

catch( Exception err ) {
    currentBuild.result = 'FAILURE'
    send_message_when_job_fail()
}