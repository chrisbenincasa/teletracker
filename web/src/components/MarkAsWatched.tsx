import React, { Component } from 'react';
import {
  Button,
  createStyles,
  Theme,
  Tooltip,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { Check } from '@material-ui/icons';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import withUser, { WithUserProps } from '../components/withUser';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../actions/user';
import AuthDialog from './Auth/AuthDialog';
import { ActionType } from '../types';
import Thing from '../types/Thing';
import moment from 'moment';

const styles = (theme: Theme) =>
  createStyles({
    button: {
      marginTop: theme.spacing(1),
    },
    buttonIcon: {
      marginRight: theme.spacing(1),
    },
    itemCTA: {
      width: '100%',
      whiteSpace: 'nowrap',
    },
  });

interface OwnProps {
  itemDetail: Thing;
  style: object;
}

interface DispatchProps {
  updateUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
  removeUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;

interface State {
  loginModalOpen: boolean;
}

class MarkAsWatched extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      loginModalOpen: false,
    };
  }

  toggleItemWatched = (): void => {
    let payload = {
      thingId: this.props.itemDetail.id,
      action: ActionType.Watched,
    };

    if (!this.props.userSelf) {
      this.toggleLoginModal();
    } else {
      if (this.itemMarkedAsWatched()) {
        this.props.removeUserItemTags(payload);
      } else {
        this.props.updateUserItemTags(payload);
      }
    }
  };

  itemMarkedAsWatched = (): boolean => {
    if (this.props.itemDetail) {
      return this.props.itemDetail.itemMarkedAsWatched;
    }

    return false;
  };

  watchedButton = (isReleased: boolean) => {
    const { classes } = this.props;
    const watchedStatus = this.itemMarkedAsWatched();
    const watchedCTA = watchedStatus ? 'Mark as unwatched' : 'Mark as watched';

    return (
      <Button
        size="small"
        variant="contained"
        aria-label={watchedCTA}
        onClick={this.toggleItemWatched}
        fullWidth
        disabled={!isReleased}
        className={classes.button}
        color={watchedStatus ? 'primary' : undefined}
        startIcon={<Check className={classes.buttonIcon} />}
      >
        {watchedCTA}
      </Button>
    );
  };

  toggleLoginModal = (): void => {
    this.setState({ loginModalOpen: !this.state.loginModalOpen });
  };

  render() {
    const { classes, style } = this.props;
    const currentDate = moment();
    const releaseDate = moment(this.props.itemDetail.release_date);
    const isReleased = currentDate.diff(releaseDate, 'days') >= 0;

    return (
      <React.Fragment>
        <div className={classes.itemCTA} style={{ ...style }}>
          {!isReleased ? (
            <Tooltip title={`This is currently unreleased.`} placement="top">
              <span>{this.watchedButton(isReleased)}</span>
            </Tooltip>
          ) : (
            this.watchedButton(isReleased)
          )}
        </div>
        <AuthDialog
          open={this.state.loginModalOpen}
          onClose={this.toggleLoginModal}
        />
      </React.Fragment>
    );
  }
}

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      updateUserItemTags,
      removeUserItemTags,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(connect(null, mapDispatchToProps)(MarkAsWatched)),
);
