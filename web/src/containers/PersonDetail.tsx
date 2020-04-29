import {
  Button,
  CardMedia,
  Hidden,
  LinearProgress,
  Typography,
  Fade,
} from '@material-ui/core';
import { ChevronLeft, ExpandLess, ExpandMore } from '@material-ui/icons';
import _ from 'lodash';
import { default as React, useCallback, useEffect, useState } from 'react';
import { personFetchInitiated } from '../actions/people/get_person';
import imagePlaceholder from '../../public/images/imagePlaceholder.png';
import CreateDynamicListDialog from '../components/Dialogs/CreateDynamicListDialog';
import ManageTrackingButton from '../components/Buttons/ManageTrackingButton';
import ResponsiveImage from '../components/ResponsiveImage';
import { Person } from '../types/v2/Person';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../utils/searchFilters';
import ShareButton from '../components/Buttons/ShareButton';
import { useRouter } from 'next/router';
import { useDispatchAction } from '../hooks/useDispatchAction';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from '../hooks/useStateSelector';
import dequal from 'dequal';
import { useGenres, useNetworks } from '../hooks/useStateMetadata';
import { useWidth } from '../hooks/useWidth';
import useToggleCallback from '../hooks/useToggleCallback';
import useStyles from '../components/PersonDetail/PersonDetail.styles';
import { selectPerson } from '../components/PersonDetail/hooks';
import PersonCredits from '../components/PersonDetail/PersonCredits';
import WithItemFilters from '../components/Filters/FilterContext';

export const DEFAULT_CREDITS_FILTERS: FilterParams = {
  sortOrder: 'popularity',
};

interface NewProps {
  preloaded?: boolean;
}

function PersonDetail(props: NewProps) {
  const classes = useStyles();
  const router = useRouter();
  const width = useWidth();
  const personId = router.query.id as string;

  const [backdropLoaded, setBackdropLoaded] = useState(false);
  const imageLoadedCallback = useCallback(() => {
    setBackdropLoaded(true);
  }, []);
  const [showFullBiography, setShowFullBiography] = useState(false);
  const [createPersonListDialogOpen, setCreatePersonListDialogOpen] = useState(
    false,
  );
  const [needsFetch, setNeedsFetch] = useState(false);
  const itemById = useStateSelector(
    state => state.itemDetail.thingsById,
    dequal,
  );
  const [person, prevPerson] = useStateSelectorWithPrevious(state =>
    selectPerson(state, personId),
  );
  const [loadingPerson, prevLoadingPerson] = useStateSelectorWithPrevious(
    state => state.people.loadingPeople,
  );
  const networks = useNetworks();
  const genres = useGenres();

  let dispatchPersonFetch = useDispatchAction(personFetchInitiated);

  //
  // Effects
  //

  // When the component is mounted, determine if we need a refetch of the person
  // due to missing filmography data from previous fetches.
  useEffect(() => {
    if (person) {
      let reallyNeedsFetch =
        _.isUndefined(person.cast_credit_ids) ||
        person.cast_credit_ids.data.length === 0 ||
        _.some(person.cast_credit_ids.data, creditId =>
          _.isUndefined(itemById[creditId]),
        );
      setNeedsFetch(reallyNeedsFetch);
    } else {
      setNeedsFetch(true);
    }
  }, []);

  // Re-fetch the person when the needsFetch flag is turned to true.
  useEffect(() => {
    if (needsFetch) {
      dispatchPersonFetch({
        id: router.query.id as string,
        forDetailPage: true,
      });
    }
  }, [needsFetch]);

  // Turn off needsFetch is a re-fetch completed.
  useEffect(() => {
    if ((!prevPerson && person) || (prevLoadingPerson && !loadingPerson)) {
      setNeedsFetch(false);
    }
  }, [person, loadingPerson]);

  const toggleShowFullBio = useToggleCallback(setShowFullBiography);

  //
  // Biz Logic
  //

  const openCreateListDialog = useCallback(() => {
    setCreatePersonListDialogOpen(true);
  }, []);

  const closeCreateListDialog = useCallback(() => {
    setCreatePersonListDialogOpen(false);
  }, []);

  const personFiltersForCreateDialog = useCallback(() => {
    return {
      ...DEFAULT_FILTER_PARAMS,
      people: [person!.canonical_id],
    } as FilterParams;
  }, [person]);

  //
  // Render
  //

  const renderLoading = () => {
    return (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    );
  };

  const renderTitle = (person: Person) => {
    return (
      <div className={classes.titleContainer}>
        <Typography
          color="inherit"
          variant="h2"
          itemProp="name"
          style={{ lineHeight: 0.85 }}
        >
          {`${person.name}`}
        </Typography>
      </div>
    );
  };

  const renderDescriptiveDetails = (person: Person) => {
    const biography = person.biography || '';
    const isMobile = ['xs', 'sm'].includes(width);
    const truncateSize = isMobile ? 300 : 1200;

    const truncatedBio = showFullBiography
      ? biography
      : biography.substr(0, truncateSize);
    const formattedBiography = truncatedBio
      .split('\n')
      .filter(s => s.length > 0)
      .map((part, index) => (
        <React.Fragment key={index}>
          <Typography color="inherit">{part}</Typography>
          <br />
        </React.Fragment>
      ));

    return (
      <div className={classes.descriptionContainer}>
        <div
          style={{
            display: 'flex',
            marginBottom: 8,
            flexDirection: 'column',
            alignItems: 'self-start',
            color: '#fff',
          }}
        >
          <Hidden smDown>{renderTitle(person)}</Hidden>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <Typography color="inherit" variant="h5" className={classes.header}>
            Biography
          </Typography>
          <React.Fragment>{formattedBiography}</React.Fragment>
          {biography.length > truncateSize ? (
            <Button
              size="small"
              variant="contained"
              aria-label={showFullBiography ? 'Read Less' : 'Read More'}
              onClick={toggleShowFullBio}
              style={{ marginTop: 5, display: 'flex', alignSelf: 'center' }}
            >
              {showFullBiography ? (
                <ExpandLess style={{ marginRight: 8 }} />
              ) : (
                <ExpandMore style={{ marginRight: 8 }} />
              )}
              {showFullBiography ? 'Read Less' : 'Read More'}
            </Button>
          ) : null}
        </div>
      </div>
    );
  };

  const renderPerson = () => {
    const isMobile = ['xs', 'sm'].includes(width);
    const backdrop = person?.cast_credit_ids?.data
      .map(itemId => {
        let item = itemById[itemId];
        if (item?.backdropImage) {
          return item;
        }
      })
      .find(item => !_.isUndefined(item));

    return (
      <div className={classes.backdrop}>
        {backdrop && (
          <React.Fragment>
            <Fade in={backdropLoaded}>
              <div className={classes.backdropContainer}>
                <ResponsiveImage
                  item={backdrop}
                  imageType="backdrop"
                  imageStyle={{
                    objectFit: 'cover',
                    objectPosition: 'center top',
                    width: '100%',
                    height: '100%',
                    pointerEvents: 'none', // Disables ios preview on tap & hold
                  }}
                  loadCallback={imageLoadedCallback}
                />
                <div className={classes.backdropGradient} />
              </div>
            </Fade>
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'flex-start',
              }}
            >
              {!isMobile && (
                <Button
                  size="small"
                  onClick={router.back}
                  variant="contained"
                  aria-label="Go Back"
                  style={{ marginTop: 20, marginLeft: 20 }}
                >
                  <ChevronLeft style={{ marginRight: 8 }} />
                  Go Back
                </Button>
              )}
              <div className={classes.personDetailContainer}>
                <div className={classes.leftContainer}>
                  <Hidden mdUp>{renderTitle(person)}</Hidden>
                  <div className={classes.posterContainer}>
                    <CardMedia
                      src={imagePlaceholder}
                      item={person}
                      component={ResponsiveImage}
                      imageType="profile"
                      imageStyle={{
                        width: '100%',
                        boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                      }}
                    />
                    <div className={classes.trackingButton}>
                      <ManageTrackingButton
                        cta={'Track Actor'}
                        onClick={openCreateListDialog}
                      />
                    </div>
                    <div className={classes.trackingButton}>
                      <ShareButton
                        title={person.name}
                        text={''}
                        url={window.location.href}
                      />
                    </div>
                  </div>
                </div>
                <div className={classes.personInformationContainer}>
                  {renderDescriptiveDetails(person)}
                  <WithItemFilters defaultFilters={DEFAULT_CREDITS_FILTERS}>
                    <PersonCredits person={person} />
                  </WithItemFilters>
                </div>
              </div>
            </div>
            <CreateDynamicListDialog
              filters={personFiltersForCreateDialog()}
              open={createPersonListDialogOpen}
              onClose={closeCreateListDialog}
              networks={networks || []}
              genres={genres || []}
              prefilledName={person!.name}
            />
          </React.Fragment>
        )}
      </div>
    );
  };

  return (
    <div>
      <React.Fragment>
        <Fade in={person && !loadingPerson && !needsFetch}>
          <div style={{ width: '100%' }}>{renderPerson()}</div>
        </Fade>
        {!person || loadingPerson || needsFetch ? renderLoading() : null}
      </React.Fragment>
    </div>
  );
}

// PersonDetailF.whyDidYouRender = true;

export default PersonDetail;
