import React from 'react';
import { createStyles, makeStyles, Theme, Typography } from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import { Id } from '../types/v2';
import useStateSelector from '../hooks/useStateSelector';
import selectItem from '../selectors/selectItem';
import { getVoteAverage, getVoteCountFormatted } from '../utils/textHelper';
import useIsMobile from '../hooks/useIsMobile';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    ratingContainer: {
      display: 'flex',
      flexDirection: 'row',
    },
    ratingVoteCount: {
      marginRight: theme.spacing(1),
      fontStyle: 'italic',
      fontSize: 12,
      alignSelf: 'center',
      opacity: 0.5,
      [theme.breakpoints.down('sm')]: {
        display: 'none',
      },
    },
  }),
);

interface OwnProps {
  readonly itemId: Id;
  readonly showVoteCount: boolean;
}

type Props = OwnProps;

export default function StarRatings(props: Props) {
  const classes = useStyles();
  const isMobile = useIsMobile();
  const itemDetail = useStateSelector(state => selectItem(state, props.itemId));
  const voteAverage = getVoteAverage(itemDetail);
  const voteCount = getVoteCountFormatted(itemDetail);

  return (
    <div className={classes.ratingContainer}>
      <Rating
        size={isMobile ? 'small' : 'medium'}
        value={voteAverage}
        precision={0.1}
        readOnly
      />
      {props.showVoteCount && (
        <Typography
          color="inherit"
          variant="body1"
          className={classes.ratingVoteCount}
        >
          {`(${voteCount})`}
        </Typography>
      )}
    </div>
  );
}

StarRatings.defaultProps = {
  showVoteCount: true,
};
