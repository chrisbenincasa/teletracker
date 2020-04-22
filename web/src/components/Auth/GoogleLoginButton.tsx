import { Button, createStyles, makeStyles, Theme } from '@material-ui/core';
import React from 'react';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    googleButtonIcon: {
      height: 30,
    },
    googleButtonText: {
      marginLeft: theme.spacing(1),
    },
  }),
);

interface GoogleButtonProps {
  onClick: () => any;
}

export default function GoogleLoginButton(props: GoogleButtonProps) {
  const classes = useStyles();

  return (
    <Button variant="contained" onClick={() => props.onClick()}>
      <img
        className={classes.googleButtonIcon}
        src="/images/google_signin/Google__G__Logo.svg"
        alt="Google Logo"
      />
      <span className={classes.googleButtonText}>Sign In with Google</span>
    </Button>
  );
}
