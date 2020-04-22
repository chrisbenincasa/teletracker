import React, { Component } from 'react';
import {
  Button,
  createStyles,
  Theme,
  Tooltip,
  WithStyles,
  withStyles,
  Zoom,
} from '@material-ui/core';
import { CheckBox, ThumbUp, ThumbDown } from '@material-ui/icons';
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
import moment from 'moment';
import { Item, itemHasTag, getItemTagNumberValue } from '../../types/v2/Item';
import { ACTION_ENJOYED, ACTION_WATCHED } from '../../actions/item-detail';

const styles = (theme: Theme) =>
  createStyles({
    itemCTA: {
      whiteSpace: 'nowrap',
    },
    ratingButton: {
      minWidth: 40,
    },
    ratingButtonDislike: {
      minWidth: 40,
      '&:hover': { backgroundColor: theme.custom.palette.cancel },
    },
    ratingButtonDislikeActive: {
      minWidth: 40,
      backgroundColor: theme.custom.palette.cancel,
      '&:hover': { backgroundColor: theme.custom.palette.cancel },
    },
    ratingButtonWrapper: {
      marginLeft: theme.spacing(0.5),
    },
  });

interface OwnProps {
  itemDetail: Item;
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
}

class MarkAsWatched extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      loginModalOpen: false,
    };
  }

  toggleItemWatched = (): void => {
    const itemWatched = itemHasTag(this.props.itemDetail, ActionType.Watched);
    let payload = {
      itemId: this.props.itemDetail.id,
      action: ActionType.Watched,
    };

    if (!this.props.userSelf) {
      this.toggleLoginModal();
    } else {
      if (itemWatched) {
        this.props.removeUserItemTags(payload);
      } else {
        this.props.updateUserItemTags(payload);
      }
    }
  };

  watchedButton = (isReleased: boolean) => {
    const { classes } = this.props;
    const watchedStatus = itemHasTag(this.props.itemDetail, ActionType.Watched);
    const watchedCTA = watchedStatus ? 'Watched' : 'Mark as Watched';

    return (
      <div style={{ display: 'flex', flexDirection: 'row' }}>
        <Button
          size="small"
          variant="contained"
          aria-label={watchedCTA}
          onClick={this.toggleItemWatched}
          fullWidth
          disabled={!isReleased}
          color={watchedStatus ? 'primary' : undefined}
          startIcon={watchedStatus ? <CheckBox /> : null}
          className={classes.itemCTA}
        >
          {watchedCTA}
        </Button>
        {watchedStatus && this.ratingButton()}
      </div>
    );
  };

  toggleItemRating = (rating: number) => {
    let payload = {
      itemId: this.props.itemDetail.id,
      action: ActionType.Enjoyed,
      value: rating,
    };

    const userItemRating = getItemTagNumberValue(
      this.props.itemDetail,
      ActionType.Enjoyed,
    );

    if (userItemRating === rating) {
      this.props.removeUserItemTags(payload);
    } else {
      this.props.updateUserItemTags(payload);
    }
  };

  ratingButton = () => {
    const { classes } = this.props;
    const userItemRating = getItemTagNumberValue(
      this.props.itemDetail,
      ActionType.Enjoyed,
    );

    return (
      <React.Fragment>
        <Tooltip title="Liked it" placement="top">
          <div className={classes.ratingButtonWrapper}>
            <Zoom in={true}>
              <Button
                aria-label="Liked it"
                size="small"
                variant="contained"
                className={classes.ratingButton}
                onClick={() => this.toggleItemRating(1)}
                color={userItemRating === 1 ? 'primary' : 'secondary'}
              >
                <ThumbUp />
              </Button>
            </Zoom>
          </div>
        </Tooltip>
        <Tooltip title={"Didn't like it"} placement="top">
          <div className={classes.ratingButtonWrapper}>
            <Zoom in={true}>
              <Button
                aria-label={"Didn't like it"}
                size="small"
                variant="contained"
                className={
                  userItemRating === 0
                    ? classes.ratingButtonDislikeActive
                    : classes.ratingButtonDislike
                }
                onClick={() => this.toggleItemRating(0)}
                color={userItemRating === 0 ? 'primary' : 'secondary'}
              >
                <ThumbDown />
              </Button>
            </Zoom>
          </div>
        </Tooltip>
      </React.Fragment>
    );
  };

  toggleLoginModal = (): void => {
    this.setState({ loginModalOpen: !this.state.loginModalOpen });
  };

  render() {
    const { className, style } = this.props;
    const currentDate = moment();
    const releaseDate = moment(this.props.itemDetail.release_date);
    const isReleased = currentDate.diff(releaseDate, 'days') >= 0;

    return (
      <React.Fragment>
        <div className={className} style={{ ...style }}>
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
