#!/bin/bash

DEPLOY_TYPE=$1

if [ "${DEPLOY_TYPE}" = "master" ] || [ "${DEPLOY_TYPE}" = "DashPay" ] || [ "${TRAVIS_TAG:0:4}" = "NMA-" ] || [ "${TRAVIS_TAG:0:4}" = "dpl-" ]; then

  cd "$TRAVIS_BUILD_DIR" || exit

  if [ "${DEPLOY_TYPE}" = "master" ] || [ "${DEPLOY_TYPE}" = "DashPay" ]; then
    TRAVIS_TAG=$DEPLOY_TYPE
  fi

  eval "$(ssh-agent -s)" # Start ssh-agent cache
  chmod 600 .deploy/id_rsa # Allow read access to the private key
  ssh-add .deploy/id_rsa # Add the private key to SSH

  echo -e "Let's deploy it!"
  echo "TRAVIS_TAG: $TRAVIS_TAG"
#  ls -l wallet/build/outputs/apk/_testNet3/debug/
  git clone git@github.com:dash-mobile-team/dash-wallet-staging.git
  DEPLOY_DIR=dash-wallet-staging/"$TRAVIS_TAG"
  mkdir -p "$DEPLOY_DIR"
  cp wallet/build/outputs/apk/_testNet3/debug/wallet-_testNet3-debug.apk "$DEPLOY_DIR"/dash-wallet-_testNet3-debug.apk
#  cp wallet/build/outputs/apk/mobile/debug/wallet-mobile-debug.apk "$DEPLOY_DIR"/dash-wallet-mobile-debug.apk
#  cp wallet/build/outputs/apk/prod/debug/wallet-prod-debug.apk "$DEPLOY_DIR"/dash-wallet-prod-debug.apk
#  cp wallet/build/outputs/apk/devNet/debug/wallet-devNet-debug.apk "$DEPLOY_DIR"/dash-wallet-palinka-debug.apk
#  cp wallet/build/outputs/apk/evonet/debug/wallet-evonet-debug.apk "$DEPLOY_DIR"/dash-wallet-evonet-debug.apk
#  cp wallet/build/outputs/apk/prod/debug/wallet-prod-release.apk "$DEPLOY_DIR"/dash-wallet-prod-release.apk

  # generate README.md file
  README="$DEPLOY_DIR"/README.md
#  if [ "${TRAVIS_TAG:0:4}" = "NMA-" ]; then
#    printf 'https://dashpay.atlassian.net/browse/%s\n\n' "$TRAVIS_TAG" > "$README"
#  fi
  {
    echo "### Test builds:"
#    echo "* [dash-wallet-prod-debug.apk](https://github.com/dash-mobile-team/dash-wallet-staging/raw/master/$TRAVIS_TAG/wallet-prod-debug.apk)"
    echo "* [dash-wallet-_testNet3-debug.apk](https://github.com/dash-mobile-team/dash-wallet-staging/raw/master/$TRAVIS_TAG/wallet-_testNet3-debug.apk)"
#    echo "* [dash-wallet-mobile-debug.apk](https://github.com/dash-mobile-team/dash-wallet-staging/raw/master/$TRAVIS_TAG/wallet-mobile-debug.apk)"
#    echo "* [dash-wallet-palinka-debug.apk](https://github.com/dash-mobile-team/dash-wallet-staging/raw/master/$TRAVIS_TAG/wallet-palinka-debug.apk)"
#    echo "* [dash-wallet-evonet-debug.apk](https://github.com/dash-mobile-team/dash-wallet-staging/raw/master/$TRAVIS_TAG/wallet-evonet-debug.apk)"
    if [ "${TRAVIS_TAG:0:4}" = "NMA-" ]; then
      printf '\n\nhttps://dashpay.atlassian.net/browse/%s\n\n' "$TRAVIS_TAG"
    fi
    echo "### Deploy info:"
    # print the content of `git show "$TRAVIS_TAG"` into README.md until the first occurence of 'diff'
    # the very last line containing 'diff' is removed by | head -n -1
    git show "$TRAVIS_TAG" | grep -m1 -B20 "diff" | head -n -1
  } > "$README"

  cd dash-wallet-staging || exit
  git add .
  git commit -m "Travis CI deploy $TRAVIS_TAG"
  git push origin master

  # clean up the mess
  cd "$TRAVIS_BUILD_DIR" || exit
  rm -rf dash-wallet-staging
  rm -rf "$TRAVIS_BUILD_DIR"/app/build/outputs
  if [ "${TRAVIS_TAG:0:4}" = "NMA-" ] || [ "${TRAVIS_TAG:0:4}" = "dpl-" ]; then
    echo "deleting tag $TRAVIS_TAG"
#    git tag -d "$TRAVIS_TAG"
    git push -q https://"$PERSONAL_ACCESS_TOKEN"@github.com/dashevo/dash-wallet --delete "refs/tags/$TRAVIS_TAG"
  fi
else
  echo "Only tags, master or DashPay"
fi
echo "Deploy done"
