import React, { Component } from 'react';
import {
  Button,
  createStyles,
  Theme,
  Tooltip,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { CheckBox, CheckBoxOutlineBlank } from '@material-ui/icons';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import withUser, { WithUserProps } from '../withUser';
import {
  removeUserItemTags,
  updateUserItemTags,
  UserUpdateItemTagsPayload,
} from '../../actions/user';
import AuthDialog from '../Auth/AuthDialog';
import { ActionType } from '../../types';
import Thing from '../../types/Thing';
import moment from 'moment';

const styles = (theme: Theme) =>
  createStyles({
    buttonIcon: {
      // marginRight: theme.spacing(),
    },
    itemCTA: {
      whiteSpace: 'nowrap',
    },
  });

interface OwnProps {
  itemDetail: Thing;
  style: object;
  className?: string;
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
  watched?: boolean;
}

class MarkAsWatched extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      loginModalOpen: false,
      watched: this.itemMarkedAsWatched(),
    };
  }

  toggleItemWatched = (): void => {
    let payload = {
      itemId: this.props.itemDetail.id,
      action: ActionType.Watched,
    };

    if (!this.props.userSelf) {
      this.toggleLoginModal();
    } else {
      if (this.state.watched) {
        this.props.removeUserItemTags(payload);
        this.setState({ watched: false });
      } else {
        this.props.updateUserItemTags(payload);
        this.setState({ watched: true });
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
    const watchedStatus = this.state.watched;
    const watchedCTA = watchedStatus ? 'Watched' : 'Mark Watched';

    return (
      <Button
        size="small"
        variant="contained"
        aria-label={watchedCTA}
        onClick={this.toggleItemWatched}
        fullWidth
        disabled={!isReleased}
        color={watchedStatus ? 'primary' : undefined}
        startIcon={
          watchedStatus ? <CheckBox className={classes.buttonIcon} /> : null
        }
        className={classes.itemCTA}
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
        <div className={this.props.className} style={{ ...style }}>
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
