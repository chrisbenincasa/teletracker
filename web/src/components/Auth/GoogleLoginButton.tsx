import { Button, createStyles, Theme, WithStyles } from '@material-ui/core';
import React, { FunctionComponent } from 'react';
import withStyles from '@material-ui/core/styles/withStyles';

const styles = (theme: Theme) =>
  createStyles({
    googleButtonIcon: {
      height: 30,
    },
    googleButtonText: {
      marginLeft: theme.spacing(1),
    },
  });

interface GoogleButtonProps {
  onClick: () => any;
}

type Props = GoogleButtonProps & WithStyles<typeof styles>;

const GoogleLoginButton: FunctionComponent<Props> = (props: Props) => {
  const { classes } = props;

  return (
    <Button variant="outlined" onClick={() => props.onClick()}>
      <img
        className={classes.googleButtonIcon}
        src="/images/google_signin/Google__G__Logo.svg"
        alt="Google Logo"
      />
      <span className={classes.googleButtonText}>Sign In with Google</span>
    </Button>
  );
};

export default withStyles(styles)(GoogleLoginButton);
