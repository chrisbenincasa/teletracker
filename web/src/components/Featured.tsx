import {
  CardMedia,
  Fade,
  Grow,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { Rating } from '@material-ui/lab';
import React, { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import ManageTracking from '../components/ManageTracking';
import { ResponsiveImage } from '../components/ResponsiveImage';
import { Item } from '../types/v2/Item';
import AddToListDialog from './AddToListDialog';
import withUser, { WithUserProps } from './withUser';
import {
  formatRuntime,
  getVoteAverage,
  getVoteCount,
  truncateText,
} from '../utils/textHelper';

const useStyles = makeStyles((theme: Theme) => ({
  backdropContainer: {
    height: 'auto',
    overflow: 'hidden',
    top: 0,
    width: '100%',
  },
  featuredItem: {
    position: 'relative',
    margin: theme.spacing(1),
    [theme.breakpoints.down('sm')]: {
      margin: 0,
    },
  },
  posterContainer: {
    display: 'flex',
    flex: '0 1 auto',
    flexDirection: 'column',
    position: 'absolute',
    height: 'auto',
    top: '5%',
    left: theme.spacing(3),
    width: '25%',
    [theme.breakpoints.up('md')]: {
      width: '30%',
    },
  },
  ratingContainer: {
    display: 'flex',
    flexDirection: 'row',
  },
  ratingVoteCount: {
    marginRight: 10,
    [theme.breakpoints.down('sm')]: {
      display: 'none',
    },
  },
  title: {
    textAlign: 'right',
    alignSelf: 'flex-end',
    fontSize: theme.typography.h5.fontSize,
    fontWeight: 700,
  },
  titleContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    position: 'absolute',
    bottom: 0,
    right: 0,
    padding: theme.spacing(1),
    marginBottom: theme.spacing(2),
    backgroundColor: 'rgba(48, 48, 48, 0.6)',
    maxWidth: '50%',
  },
  wrapper: {
    display: 'flex',
    flexGrow: 1,
    flexDirection: 'row',
    margin: theme.spacing(2),
    [theme.breakpoints.down('sm')]: {
      margin: theme.spacing(1),
    },
  },
}));

interface OwnProps {
  featuredItems: Item[];
}

type Props = OwnProps & WithUserProps;

function Featured(props: Props) {
  const [manageTrackingModalOpen, setManageTrackingModalOpen] = useState<
    boolean
  >(false);
  const [imageLoading, setImageLoading] = useState<boolean>(true);
  const classes = useStyles();
  const { featuredItems, userSelf } = props;

  useEffect(() => {
    setImageLoading(true);
    return setImageLoading(false);
  }, [featuredItems]);

  const renderTitle = (item: Item) => {
    const voteAverage = getVoteAverage(item);
    const voteCount = getVoteCount(item);
    const runtime =
      (item.runtime && formatRuntime(item.runtime, item.type)) || null;

    return (
      <div className={classes.titleContainer}>
        <Typography color="inherit" variant="h4" itemProp="name">
          {truncateText(item.canonicalTitle, 200)}
        </Typography>
        <div className={classes.ratingContainer}>
          <Rating value={voteAverage} precision={0.1} readOnly />
          <Typography
            color="inherit"
            variant="body1"
            className={classes.ratingVoteCount}
          >
            {`(${voteCount})`}
          </Typography>
        </div>
        <Typography color="inherit" variant="body1" itemProp="duration">
          {runtime}
        </Typography>
      </div>
    );
  };

  const imageLoaded = () => {
    setImageLoading(false);
  };

  const renderFeaturedItems = () => {
    return featuredItems && featuredItems.length > 0
      ? featuredItems.map(item => {
          return (
            <Fade in={!imageLoading} key={item.id}>
              <div
                className={classes.featuredItem}
                style={{ width: `${100 / featuredItems.length}%` }}
              >
                <div className={classes.backdropContainer}>
                  <ResponsiveImage
                    item={item}
                    imageType="backdrop"
                    imageStyle={{
                      objectFit: 'cover',
                      width: '100%',
                      height: '100%',
                      maxHeight: 424,
                      borderRadius: 10,
                    }}
                    pictureStyle={{
                      display: 'block',
                    }}
                    loadCallback={imageLoaded}
                  />
                </div>

                <div className={classes.posterContainer}>
                  <RouterLink
                    to={item.relativeUrl}
                    style={{
                      display: 'block',
                      height: '100%',
                      textDecoration: 'none',
                    }}
                  >
                    <CardMedia
                      src={imagePlaceholder}
                      item={item}
                      component={ResponsiveImage}
                      imageType="poster"
                      imageStyle={{
                        boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                        maxWidth: '100%',
                        maxHeight: 340,
                        borderRadius: 10,
                      }}
                      pictureStyle={{
                        height: '100%',
                        width: '100%',
                        display: 'block',
                      }}
                    />
                  </RouterLink>
                  <ManageTracking itemDetail={item} style={{ maxWidth: 225 }} />

                  <AddToListDialog
                    open={manageTrackingModalOpen}
                    onClose={() => setManageTrackingModalOpen(false)}
                    userSelf={userSelf!}
                    item={item}
                  />
                </div>
                <Grow in={!imageLoading} timeout={500}>
                  {renderTitle(item)}
                </Grow>
              </div>
            </Fade>
          );
        })
      : null;
  };
  return featuredItems && featuredItems.length > 0 ? (
    <div className={classes.wrapper}>{renderFeaturedItems()}</div>
  ) : null;
}

export default withUser(Featured);
