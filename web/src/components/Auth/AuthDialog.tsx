import { Backdrop, Dialog, Theme } from '@material-ui/core';
import { createStyles } from '@material-ui/styles';
import React, { useEffect, useState } from 'react';
import LoginForm from './LoginForm';
import SignupForm from './SignupForm';
import { LoginState } from '../../actions/auth';
import { useRouter } from 'next/router';
import { makeStyles } from '@material-ui/core/styles';
import { logModalView } from '../../utils/analytics';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    paper: {
      padding: theme.spacing(2, 3, 3),
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      minHeight: 412,
    },
  }),
);

type Props = {
  open: boolean;
  onActionInitiated?: () => void;
  onClose: () => void;
  initialForm?: 'login' | 'signup';
};

function AuthDialog(props: Props) {
  const [show, setShow] = useState(props.initialForm || 'login');
  const router = useRouter();
  const classes = useStyles();

  useEffect(() => {
    if (props.initialForm) {
      setShow(props.initialForm);
    }
  }, [props.initialForm]);

  useEffect(() => {
    if (props.open) {
      logModalView(show);
    }
  }, [props.open, show]);

  const close = () => {
    props.onClose();
  };

  const onLogin = (state?: LoginState) => {
    close();

    if (state?.redirect) {
      router.replace(state.redirect.route, state.redirect.asPath, {
        shallow: true,
      });
    } else {
      router.push('/');
    }
  };

  const switchForm = () => {
    setShow(prev => (prev === 'login' ? 'signup' : 'login'));
  };

  const handleAction = () => {
    if (props.onActionInitiated) {
      props.onActionInitiated();
    }
  };

  return (
    <Dialog
      open={props.open}
      onClose={close}
      closeAfterTransition
      BackdropComponent={Backdrop}
      BackdropProps={{
        timeout: 500,
      }}
      PaperProps={{
        className: classes.paper,
      }}
    >
      {show === 'login' ? (
        <LoginForm
          onSubmitted={handleAction}
          onLogin={(state?: LoginState) => onLogin(state)}
          onNav={() => switchForm()}
        />
      ) : (
        <SignupForm onNav={() => switchForm()} />
      )}
    </Dialog>
  );
}

export default AuthDialog;
