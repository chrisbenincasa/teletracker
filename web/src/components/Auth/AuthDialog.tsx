import { Backdrop, Dialog, Theme, withStyles } from '@material-ui/core';
import { createStyles, WithStyles } from '@material-ui/styles';
import React, { Component } from 'react';
import LoginForm from './LoginForm';
import SignupForm from './SignupForm';

const styles = (theme: Theme) =>
  createStyles({
    paper: {
      padding: theme.spacing(2, 3, 3),
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
    },
  });

type OwnProps = {
  open: boolean;
  onActionInitiated?: () => void;
  onClose: () => void;
  initialForm?: 'login' | 'signup';
};

interface State {
  show: 'login' | 'signup';
}

type Props = OwnProps & WithStyles<typeof styles>;

class AuthDialog extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      show: this.props.initialForm || 'login',
    };
  }

  componentDidUpdate(prevProps: OwnProps) {
    if (
      prevProps.initialForm !== this.props.initialForm &&
      this.props.initialForm
    ) {
      this.setState({
        show: this.props.initialForm,
      });
    }
  }

  close = () => {
    this.props.onClose();
  };

  switchForm = () => {
    this.setState({
      show: this.state.show === 'login' ? 'signup' : 'login',
    });
  };

  handleAction = () => {
    if (this.props.onActionInitiated) {
      this.props.onActionInitiated();
    }
  };

  render() {
    const { classes, open } = this.props;
    const { show } = this.state;
    return (
      <Dialog
        open={open}
        onClose={this.close}
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
            onSubmitted={this.handleAction}
            onLogin={this.close}
            onNav={() => this.switchForm()}
          />
        ) : (
          <SignupForm onNav={() => this.switchForm()} />
        )}
      </Dialog>
    );
  }
}

export default withStyles(styles)(AuthDialog);
