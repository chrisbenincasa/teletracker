import React, { useState } from 'react';
import {
  Button,
  createStyles,
  makeStyles,
  Theme,
  Tooltip,
  Zoom,
} from '@material-ui/core';
import { CheckBox, ThumbDown, ThumbUp } from '@material-ui/icons';
import { removeUserItemTags, updateUserItemTags } from '../../actions/user';
import AuthDialog from '../Auth/AuthDialog';
import { ActionType } from '../../types';
import moment from 'moment';
import { getItemTagNumberValue, itemHasTag } from '../../types/v2/Item';
import { Id } from '../../types/v2';
import useStateSelector from '../../hooks/useStateSelector';
import selectItem from '../../selectors/selectItem';
import { useWithUserContext } from '../../hooks/useWithUser';
import { useDispatchAction } from '../../hooks/useDispatchAction';

const useStyles = makeStyles((theme: Theme) =>
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
  }),
);

interface OwnProps {
  readonly itemId: Id;
  readonly style?: object;
  readonly className?: string;
}

export default function MarkAsWatched(props: OwnProps) {
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const classes = useStyles();
  const itemDetail = useStateSelector(state => selectItem(state, props.itemId));
  const { isLoggedIn } = useWithUserContext();

  const wasItemWatched = itemHasTag(itemDetail, ActionType.Watched);
  const [itemWatched, setItemWatched] = useState(wasItemWatched);

  const dispatchRemoveUserItemTags = useDispatchAction(removeUserItemTags);
  const dispatchUpdateUserItemTags = useDispatchAction(updateUserItemTags);

  const toggleItemWatched = (): void => {
    let payload = {
      itemId: props.itemId,
      action: ActionType.Watched,
    };

    if (!isLoggedIn) {
      toggleLoginModal();
    } else {
      if (itemWatched) {
        setItemWatched(false);
        dispatchRemoveUserItemTags(payload);
      } else {
        setItemWatched(true);
        dispatchUpdateUserItemTags(payload);
      }
    }
  };

  const watchedButton = (isReleased: boolean) => {
    const watchedStatus = itemHasTag(itemDetail, ActionType.Watched);
    const watchedCTA = watchedStatus ? 'Watched' : 'Mark as Watched';

    return (
      <div style={{ display: 'flex', flexDirection: 'row' }}>
        <Button
          size="small"
          variant="contained"
          aria-label={watchedCTA}
          onClick={toggleItemWatched}
          fullWidth
          disabled={!isReleased}
          color={watchedStatus ? 'primary' : undefined}
          startIcon={watchedStatus ? <CheckBox /> : null}
          className={classes.itemCTA}
        >
          {watchedCTA}
        </Button>
        {watchedStatus && isReleased && ratingButton()}
      </div>
    );
  };

  const toggleItemRating = (rating: number) => {
    let payload = {
      itemId: itemDetail.id,
      action: ActionType.Enjoyed,
      value: rating,
    };

    const userItemRating = getItemTagNumberValue(
      itemDetail,
      ActionType.Enjoyed,
    );

    if (userItemRating === rating) {
      dispatchRemoveUserItemTags(payload);
    } else {
      dispatchUpdateUserItemTags(payload);
    }
  };

  const ratingButton = () => {
    const userItemRating = getItemTagNumberValue(
      itemDetail,
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
                onClick={() => toggleItemRating(1)}
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
                onClick={() => toggleItemRating(0)}
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

  const toggleLoginModal = (): void => {
    setLoginModalOpen(prev => !prev);
  };

  const currentDate = moment();
  const releaseDate = moment(itemDetail.release_date);
  const isReleased = currentDate.diff(releaseDate, 'days') >= 0;

  return (
    <React.Fragment>
      <div className={props.className} style={{ ...props.style }}>
        {!isReleased ? (
          <Tooltip title={`This is currently unreleased.`} placement="top">
            <span>{watchedButton(isReleased)}</span>
          </Tooltip>
        ) : (
          watchedButton(isReleased)
        )}
      </div>
      <AuthDialog open={loginModalOpen} onClose={toggleLoginModal} />
    </React.Fragment>
  );
}
