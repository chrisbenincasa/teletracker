import { Backdrop, Dialog, Theme, withStyles } from '@material-ui/core';
import { createStyles, WithStyles } from '@material-ui/styles';
import React from 'react';
import LoginForm from './LoginForm';

const styles = (theme: Theme) =>
  createStyles({
    paper: {
      padding: `${theme.spacing(2)}px ${theme.spacing(3)}px ${theme.spacing(
        3,
      )}px`,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
    },
  });

type Props = {
  open: boolean;
  onClose: () => void;
};

const LoginDialog = (props: Props & WithStyles<typeof styles>) => {
  const close = () => {
    props.onClose();
  };

  return (
    <Dialog
      open={props.open}
      onClose={() => close()}
      closeAfterTransition
      BackdropComponent={Backdrop}
      BackdropProps={{
        timeout: 500,
      }}
      PaperProps={{
        className: props.classes.paper,
      }}
    >
      <LoginForm />
    </Dialog>
  );
};

export default withStyles(styles)(LoginDialog);
